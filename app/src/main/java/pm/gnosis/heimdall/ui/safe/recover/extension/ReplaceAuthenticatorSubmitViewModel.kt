package pm.gnosis.heimdall.ui.safe.recover.extension

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.helpers.CryptoHelper
import pm.gnosis.heimdall.utils.AuthenticatorInfo
import pm.gnosis.heimdall.utils.AuthenticatorSetupInfo
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ReplaceAuthenticatorSubmitViewModel @Inject constructor(
    private val cryptoHelper: CryptoHelper,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val pushServiceRepository: PushServiceRepository,
    private val tokenRepository: TokenRepository,
    private val transactionExecutionRepository: TransactionExecutionRepository
) : ReplaceAuthenticatorSubmitContract() {
    private lateinit var safeTransaction: SafeTransaction
    private lateinit var signature1: Signature
    private lateinit var signature2: Signature
    private lateinit var txGas: BigInteger
    private lateinit var dataGas: BigInteger
    private lateinit var operationalGas: BigInteger
    private lateinit var gasPrice: BigInteger
    private lateinit var gasToken: Solidity.Address
    private lateinit var authenticatorInfo: AuthenticatorSetupInfo
    private lateinit var txHash: ByteArray

    override fun setup(
        safeTransaction: SafeTransaction,
        signature1: Signature,
        signature2: Signature,
        txGas: BigInteger,
        dataGas: BigInteger,
        operationalGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        authenticatorInfo: AuthenticatorSetupInfo,
        txHash: ByteArray
    ) {
        this.safeTransaction = safeTransaction
        this.signature1 = signature1
        this.signature2 = signature2
        this.txGas = txGas
        this.dataGas = dataGas
        this.operationalGas = operationalGas
        this.gasPrice = gasPrice
        this.gasToken = gasToken
        this.authenticatorInfo = authenticatorInfo
        this.txHash = txHash
    }

    override fun loadFeeInfo(): Single<ERC20TokenWithBalance> =
        tokenRepository.loadToken(gasToken).map {
            ERC20TokenWithBalance(it, requiredFunds())
        }

    override fun observeSubmitStatus(): Observable<Result<SubmitStatus>> =
        tokenRepository.loadToken(gasToken)
            .flatMapObservable { token ->
                Observable.interval(0, SAFE_BALANCE_REQUEST_INTERVAL, SAFE_BALANCE_REQUEST_TIME_UNIT)
                    .concatMap {
                        tokenRepository.loadTokenBalances(safeTransaction.wrapped.address, listOf(token))
                            .map { tokenBalances ->
                                if (tokenBalances.size != 1) throw NoTokenBalanceException()
                                tokenBalances.first().let { (token, balance) ->
                                    val balanceAfterTx = (balance ?: BigInteger.ZERO) - requiredFunds()
                                    val canSubmit = balanceAfterTx >= BigInteger.ZERO
                                    SubmitStatus(
                                        ERC20TokenWithBalance(token, balanceAfterTx),
                                        ERC20TokenWithBalance(token, balanceAfterTx - requiredFunds()),
                                        canSubmit
                                    )
                                }
                            }
                            .mapToResult()
                    }
            }

    private fun requiredFunds() = (txGas + dataGas + operationalGas) * gasPrice

    override fun getSafeTransaction() = safeTransaction

    override fun loadSafe() = gnosisSafeRepository.loadSafe(safeTransaction.wrapped.address)

    override fun submitTransaction() =
        gnosisSafeRepository.loadInfo(safeTransaction.wrapped.address)
            .firstOrError()
            .flatMap { info ->
                transactionExecutionRepository.calculateHash(
                    safeAddress = safeTransaction.wrapped.address,
                    transaction = safeTransaction,
                    txGas = txGas,
                    dataGas = dataGas,
                    gasPrice = gasPrice,
                    gasToken = gasToken,
                    version = info.version
                )
                    // Verify if transaction hash matches
                    .map { computedTxHash ->
                        if (!computedTxHash.contentEquals(txHash)) throw IllegalStateException("Invalid transaction hash")
                        else Unit
                    }
                    // Recover addresses from the signatures
                    .flatMap {
                        Single.zip(listOf(signature1, signature2).map { signature ->
                            Single.fromCallable { cryptoHelper.recover(txHash, signature) to signature }
                        }) { pairs -> (pairs.map { it as Pair<Solidity.Address, Signature> }).toList() }
                    }
                    // Submit transaction
                    .flatMap { signaturesWithAddresses ->
                        transactionExecutionRepository.submit(
                            safeAddress = safeTransaction.wrapped.address,
                            transaction = safeTransaction,
                            signatures = signaturesWithAddresses.toMap(),
                            senderIsOwner = false,
                            txGas = txGas,
                            dataGas = dataGas,
                            gasPrice = gasPrice,
                            gasToken = gasToken,
                            version = info.version,
                            addToHistory = true
                        )
                    }
            }
            .flatMapCompletable {
                gnosisSafeRepository.saveAuthenticatorInfo(authenticatorInfo.authenticator)
                if (authenticatorInfo.authenticator.type == AuthenticatorInfo.Type.EXTENSION) {
                    pushServiceRepository.propagateSafeCreation(safeTransaction.wrapped.address, setOf(authenticatorInfo.authenticator.address))
                        .onErrorComplete()
                } else {
                    Completable.complete()
                }
            }
            .mapToResult()

    companion object {
        private const val SAFE_BALANCE_REQUEST_INTERVAL = 5L
        private val SAFE_BALANCE_REQUEST_TIME_UNIT = TimeUnit.SECONDS
    }
}
