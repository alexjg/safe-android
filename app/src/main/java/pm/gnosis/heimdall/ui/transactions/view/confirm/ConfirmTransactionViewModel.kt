package pm.gnosis.heimdall.ui.transactions.view.confirm

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.RestrictedTransactionException
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.TransactionInfoRepository
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.Events
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.ViewUpdate
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.utils.hexToByteArray
import pm.gnosis.utils.removeHexPrefix
import pm.gnosis.utils.toHexString
import java.math.BigInteger
import javax.inject.Inject

class ConfirmTransactionViewModel @Inject constructor(
    private val submitTransactionHelper: SubmitTransactionHelper,
    private val accountsRepository: AccountsRepository,
    private val transactionInfoRepository: TransactionInfoRepository,
    private val executionRepository: TransactionExecutionRepository
) : ConfirmTransactionContract() {

    private lateinit var safe: Solidity.Address

    private lateinit var hash: String

    private lateinit var operationalGas: BigInteger

    private lateinit var dataGas: BigInteger

    private lateinit var txGas: BigInteger

    private lateinit var gasToken: Solidity.Address

    private lateinit var gasPrice: BigInteger

    private var nonce: BigInteger? = null

    private lateinit var signature: Signature

    private val cache = HashMap<String, TransactionExecutionRepository.SafeExecuteState>()

    override fun setup(
        safe: Solidity.Address,
        hash: String,
        operationalGas: BigInteger,
        dataGas: BigInteger,
        txGas: BigInteger,
        gasToken: Solidity.Address,
        gasPrice: BigInteger,
        nonce: BigInteger?,
        signature: Signature
    ) {
        this.safe = safe
        this.hash = hash
        this.operationalGas = operationalGas
        this.dataGas = dataGas
        this.txGas = txGas
        this.gasToken = gasToken
        this.gasPrice = gasPrice
        this.nonce = nonce
        this.signature = signature
        submitTransactionHelper.setup(safe, ::txParams, modifyTargets = ::modifyTargets)
    }

    private fun txParams(transaction: SafeTransaction): Single<TransactionExecutionRepository.ExecuteInformation> {
        // We need to set the original nonce again as it is not set by the transaction view holder
        val updatedTransaction = transaction.copy(wrapped = transaction.wrapped.copy(nonce = nonce ?: BigInteger.ZERO))
        return loadState(hash)
            .flatMap { state ->
                executionRepository.calculateHash(safe, updatedTransaction, txGas, dataGas, gasPrice, gasToken, state.version)
                    .map {
                        if (it.toHexString().toLowerCase() != hash.removeHexPrefix().toLowerCase()) {
                            throw IllegalStateException("Invalid transaction")
                        }
                        state
                    }
            }
            .map {
                TransactionExecutionRepository.ExecuteInformation(
                    hash, updatedTransaction, it.sender, it.requiredConfirmation, it.owners, it.version,
                    gasToken, gasPrice, txGas, dataGas, operationalGas, it.balance
                )
            }
    }

    private fun loadState(hash: String) =
        cache[hash]?.let { Single.just(it) } ?: executionRepository.loadSafeExecuteState(safe, gasToken)
            .doOnSuccess {
                cache[hash] = it
            }

    private fun modifyTargets(targets: MutableSet<Solidity.Address>) =
        accountsRepository.recover(hash.hexToByteArray(), signature).map<Set<Solidity.Address>> { targets.add(it); targets }

    override fun observe(events: Events, transaction: SafeTransaction): Observable<Result<ViewUpdate>> =
        transactionInfoRepository.checkRestrictedTransaction(transaction)
            .onErrorResumeNext {
                Single.error(
                    when (it) {
                        is RestrictedTransactionException.DelegateCall ->
                            InvalidTransactionException(R.string.restricted_transaction_delegatecall)
                        is RestrictedTransactionException.ModifyOwners ->
                            InvalidTransactionException(R.string.restricted_transaction_modify_signers)
                        is RestrictedTransactionException.ModifyModules ->
                            InvalidTransactionException(R.string.restricted_transaction_modify_modules)
                        is RestrictedTransactionException.ChangeThreshold ->
                            InvalidTransactionException(R.string.restricted_transaction_change_threshold)
                        is RestrictedTransactionException.ChangeMasterCopy ->
                            InvalidTransactionException(R.string.restricted_transaction_modify_proxy)
                        else ->
                            it
                    }
                )
            }
            .flatMap { transactionInfoRepository.parseTransactionData(transaction) }
            .flatMapObservable { data -> submitTransactionHelper.observe(events, data, setOf(signature)) }

    override fun rejectTransaction(transaction: SafeTransaction): Completable =
        loadState(hash)
            .flatMap { modifyTargets((it.owners - it.sender).toMutableSet()).map { targets -> targets to it.version } }
            .flatMapCompletable { (targets, version) ->
                executionRepository.notifyReject(safe, transaction, txGas, dataGas, gasPrice, gasToken, targets, version)
            }
}
