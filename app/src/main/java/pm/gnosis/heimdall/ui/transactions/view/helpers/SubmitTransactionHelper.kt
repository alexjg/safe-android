package pm.gnosis.heimdall.ui.transactions.view.helpers

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import pm.gnosis.heimdall.ERC20Contract
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.helpers.SignatureStore
import pm.gnosis.heimdall.ui.transactions.view.TransactionInfoViewHolder
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.Events
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.ViewUpdate
import pm.gnosis.heimdall.utils.emitAndNext
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.ErrorResult
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.utils.addHexPrefix
import pm.gnosis.utils.isSolidityMethod
import pm.gnosis.utils.removeSolidityMethodPrefix
import pm.gnosis.utils.toHexString
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface SubmitTransactionHelper {

    fun setup(
        safe: Solidity.Address,
        executionInfo: (SafeTransaction) -> Single<TransactionExecutionRepository.ExecuteInformation>,
        referenceId: Long? = null,
        modifyTargets: (MutableSet<Solidity.Address>) -> Single<Set<Solidity.Address>> = { targets -> Single.just(targets) }
    )

    fun observe(
        events: Events,
        transactionData: TransactionData,
        initialSignatures: Set<Signature>? = null
    ): Observable<Result<ViewUpdate>>

    data class Events(val retry: Observable<Unit>, val requestConfirmations: Observable<Unit>, val submit: Observable<Unit>)

    sealed class ViewUpdate {
        data class TransactionInfo(val viewHolder: TransactionInfoViewHolder) : ViewUpdate()
        data class Estimate(val hash: String, val fees: BigInteger, val balance: BigInteger, val token: ERC20Token, val canSubmit: Boolean) :
            ViewUpdate()

        object EstimateError : ViewUpdate()
        data class Confirmations(val isReady: Boolean) : ViewUpdate()
        object ConfirmationsRequested : ViewUpdate()
        object ConfirmationsError : ViewUpdate()
        object TransactionRejected : ViewUpdate()
        data class TransactionSubmitted(val success: Boolean, val chainHash: String? = null) : ViewUpdate()
    }
}

class DefaultSubmitTransactionHelper @Inject constructor(
    private val executionRepository: TransactionExecutionRepository,
    private val signaturePushRepository: PushServiceRepository,
    private val signatureStore: SignatureStore,
    private val tokenRepository: TokenRepository,
    private val transactionViewHolderBuilder: TransactionViewHolderBuilder
) : SubmitTransactionHelper {

    private lateinit var safe: Solidity.Address
    private lateinit var executionInfo: (SafeTransaction) -> Single<TransactionExecutionRepository.ExecuteInformation>
    private var referenceId: Long? = null
    private lateinit var modifyTargets: (MutableSet<Solidity.Address>) -> Single<Set<Solidity.Address>>

    override fun setup(
        safe: Solidity.Address,
        executionInfo: (SafeTransaction) -> Single<TransactionExecutionRepository.ExecuteInformation>,
        referenceId: Long?,
        modifyTargets: (MutableSet<Solidity.Address>) -> Single<Set<Solidity.Address>>
    ) {
        this.safe = safe
        this.referenceId = referenceId
        this.executionInfo = executionInfo
        this.modifyTargets = modifyTargets
    }

    override fun observe(
        events: Events,
        transactionData: TransactionData,
        initialSignatures: Set<Signature>?
    ): Observable<Result<ViewUpdate>> =
        transactionViewHolderBuilder.build(safe, transactionData)
            .emitAndNext(
                emit = {
                    DataResult(
                        ViewUpdate.TransactionInfo(
                            it
                        )
                    )
                },
                next = { estimation(events, it, initialSignatures) })

    private fun estimation(
        events: Events,
        viewHolder: TransactionInfoViewHolder,
        initialSignatures: Set<Signature>?
    ) =
        events.retry
            .subscribeOn(AndroidSchedulers.mainThread())
            .startWith(Unit)
            .switchMapSingle { viewHolder.loadTransaction() }
            .switchMapSingle { tx -> executionInfo(tx).mapToResult() }
            .switchMapSingle {
                it.mapSingle({ execInfo ->
                    tokenRepository.loadToken(execInfo.gasToken).map { token -> execInfo to token }.mapToResult()
                })
            }
            .map {
                it.map { (execInfo, token) ->
                    val gasCosts = execInfo.gasCosts()
                    // If we transfer our payment token we should add this to the required funds
                    val requiredFunds = gasCosts + when (execInfo.gasToken) {
                        ERC20Token.ETHER_TOKEN.address -> (execInfo.transaction.wrapped.value?.value ?: BigInteger.ZERO)
                        execInfo.transaction.wrapped.address -> {
                            if (execInfo.transaction.wrapped.data?.isSolidityMethod(ERC20Contract.Transfer.METHOD_ID) == true) {
                                val argData = execInfo.transaction.wrapped.data!!.removeSolidityMethodPrefix(ERC20Contract.Transfer.METHOD_ID)
                                ERC20Contract.Transfer.decodeArguments(argData)._value.value
                            } else {
                                BigInteger.ZERO
                            }
                        }
                        else -> BigInteger.ZERO
                    }
                    Triple(execInfo, token, execInfo.balance >= requiredFunds)
                }
            }
            .emitAndNext(
                emit = {
                    it.map { (execInfo, token, canSubmit) ->
                        ViewUpdate.Estimate(execInfo.transactionHash, execInfo.gasCosts(), execInfo.balance, token, canSubmit)
                    }
                },
                next = { confirmations(events, it, initialSignatures) })

    private fun confirmations(
        events: Events,
        params: Result<Triple<TransactionExecutionRepository.ExecuteInformation, ERC20Token, Boolean>>,
        initialSignatures: Set<Signature>?
    ) =
        (params as? DataResult)?.data?.let { (execInfo, _, canSubmit) ->
            if (canSubmit) {
                // Once we have the execution information we can setup everything related to requesting and receiving confirmation
                Observable.merge(
                    observeConfirmationStore(events, execInfo, initialSignatures),
                    observeIncomingConfirmations(execInfo)
                )
            } else {
                // We cannot submit don't do anything
                Observable.empty()
            }
        } ?: Observable.just<Result<ViewUpdate>>(
            DataResult(ViewUpdate.EstimateError)
        )

    private fun observeConfirmationStore(
        events: Events,
        params: TransactionExecutionRepository.ExecuteInformation,
        initialSignatures: Set<Signature>?
    ) =
        (initialSignatures?.let {
            Single.zip(
                it.map {
                    executionRepository.checkConfirmation(
                        safe, params.transaction, params.txGas, params.dataGas, params.gasPrice, params.gasToken, it, params.safeVersion
                    )
                }
            ) { results ->
                @Suppress("UNCHECKED_CAST")
                results.associate { entry -> (entry as Pair<Solidity.Address, Signature>) }
            }
                .onErrorReturn { emptyMap() }
        } ?: Single.just(emptyMap()))
            .flatMapObservable {
                signatureStore.flatMapInfo(
                    safe, params, it
                )
            }.publish {
                Observable.merge(
                    it.firstElement().flatMapObservable { requestConfirmation(events, params, it) },
                    it.map {
                        val threshold = params.requiredConfirmation - (if (params.isOwner) 1 else 0)
                        DataResult(
                            ViewUpdate.Confirmations(
                                it.size >= threshold
                            )
                        )
                    },
                    it.switchMap { signatures ->
                        events.submit
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .switchMap { submitTransaction(params, signatures) }
                    }
                )
            }

    private fun requestConfirmation(
        events: Events,
        params: TransactionExecutionRepository.ExecuteInformation,
        initialConfirmations: Map<Solidity.Address, Signature>
    ) =
        if ((params.requiredConfirmation == initialConfirmations.size + (if (params.isOwner) 1 else 0)))
            Observable.empty<Result<ViewUpdate>>()
        else
            events.requestConfirmations
                .subscribeOn(AndroidSchedulers.mainThread())
                .startWith(Unit)
                .flatMapSingle { signatureStore.load() }
                .switchMapSingle {
                    val targets = params.owners - params.sender - it.keys
                    if (targets.isEmpty()) {
                        // Nothing to push
                        return@switchMapSingle Single.just(DataResult(Unit))
                    }
                    executionRepository.calculateHash(
                        safe, params.transaction, params.txGas, params.dataGas, params.gasPrice, params.gasToken, params.safeVersion
                    )
                        .flatMapCompletable { hash ->
                            signaturePushRepository.requestConfirmations(
                                hash.toHexString().addHexPrefix(),
                                safe,
                                params.transaction,
                                params.txGas,
                                params.dataGas,
                                params.operationalGas,
                                params.gasPrice,
                                params.gasToken,
                                targets.toSet()
                            )

                        }
                        .mapToResult()
                }
                .flatMap {
                    when (it) {
                        is DataResult -> Observable.just(DataResult(ViewUpdate.ConfirmationsRequested))
                        is ErrorResult -> {
                            Timber.e(it.error)
                            Observable.fromArray(DataResult(ViewUpdate.ConfirmationsError))
                        }
                    }
                }

    private fun submitTransaction(params: TransactionExecutionRepository.ExecuteInformation, signatures: Map<Solidity.Address, Signature>) =
        executionRepository.submit(
            safe, params.transaction, signatures, params.isOwner, params.txGas, params.dataGas, params.gasPrice, params.gasToken, params.safeVersion,
            referenceId = referenceId
        )
            .flatMap { hash -> modifyTargets((params.owners - params.sender).toMutableSet()).map { it to hash } }
            .flatMap { (targets, hash) ->
                if (targets.isEmpty()) {
                    // Nothing to push
                    return@flatMap Single.just(hash)
                }
                signaturePushRepository.propagateSubmittedTransaction(params.transactionHash, hash, targets)
                    // Ignore error here ... if push fails ... it fails
                    .doOnError(Timber::e)
                    .onErrorComplete()
                    .toSingleDefault(hash)
            }
            .flatMapObservable {
                Observable.just<ViewUpdate>(
                    ViewUpdate.TransactionSubmitted(
                        true, it
                    )
                )
            }
            .onErrorResumeNext { t: Throwable ->
                // Propagate error to display snackbar then propagate status
                Observable.just<ViewUpdate>(
                    ViewUpdate.TransactionSubmitted(
                        false
                    )
                ).concatWith(Observable.error(t))
            }
            .mapToResult()

    private fun observeIncomingConfirmations(params: TransactionExecutionRepository.ExecuteInformation) =
        signaturePushRepository.observe(params.transactionHash)
            .flatMap {
                when (it) {
                    is PushServiceRepository.TransactionResponse.Confirmed ->
                        executionRepository.checkConfirmation(
                            safe, params.transaction, params.txGas, params.dataGas, params.gasPrice, params.gasToken, it.signature, params.safeVersion
                        )
                            .map(signatureStore::add)
                            .flatMapObservable { Observable.empty<Result<ViewUpdate>>() }
                            .onErrorResumeNext { e: Throwable -> Observable.just(ErrorResult(e)) }
                    is PushServiceRepository.TransactionResponse.Rejected ->
                        executionRepository.checkRejection(
                            safe, params.transaction, params.txGas, params.dataGas, params.gasPrice, params.gasToken, it.signature, params.safeVersion
                        )
                            .filter { (sender) -> params.owners.contains(sender) }
                            .map<Result<ViewUpdate>> { DataResult(ViewUpdate.TransactionRejected) }
                            .toObservable()
                            .onErrorResumeNext { e: Throwable -> Observable.just(ErrorResult(e)) }
                }
            }

}
