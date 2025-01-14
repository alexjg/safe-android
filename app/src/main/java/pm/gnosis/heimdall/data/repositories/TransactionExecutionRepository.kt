package pm.gnosis.heimdall.data.repositories

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.data.repositories.models.SemVer
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import java.math.BigInteger


interface TransactionExecutionRepository {

    fun addTransactionEventsCallback(callback: TransactionEventsCallback): Boolean

    fun removeTransactionEventsCallback(callback: TransactionEventsCallback): Boolean

    fun calculateHash(
        safeAddress: Solidity.Address, transaction: SafeTransaction,
        txGas: BigInteger, dataGas: BigInteger, gasPrice: BigInteger, gasToken: Solidity.Address,
        version: SemVer
    ): Single<ByteArray>

    fun loadSafeExecuteState(safeAddress: Solidity.Address, paymentToken: Solidity.Address): Single<SafeExecuteState>
    fun loadExecuteInformation(
        safeAddress: Solidity.Address,
        paymentToken: Solidity.Address,
        transaction: SafeTransaction,
        safeOwner: AccountsRepository.SafeOwner? = null
    ): Single<ExecuteInformation>

    fun signConfirmation(
        safeAddress: Solidity.Address,
        transaction: SafeTransaction,
        txGas: BigInteger,
        dataGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        version: SemVer
    ): Single<Signature>

    fun signRejection(
        safeAddress: Solidity.Address,
        transaction: SafeTransaction,
        txGas: BigInteger,
        dataGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        version: SemVer
    ): Single<Signature>

    fun checkConfirmation(
        safeAddress: Solidity.Address,
        transaction: SafeTransaction,
        txGas: BigInteger,
        dataGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        signature: Signature,
        version: SemVer
    ): Single<Pair<Solidity.Address, Signature>>

    fun checkRejection(
        safeAddress: Solidity.Address,
        transaction: SafeTransaction,
        txGas: BigInteger,
        dataGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        signature: Signature,
        version: SemVer
    ): Single<Pair<Solidity.Address, Signature>>

    fun observePublishStatus(id: String): Observable<PublishStatus>

    fun observeTransactionStatus(transactionHash: BigInteger): Observable<Pair<Boolean, Long>>

    fun submit(
        safeAddress: Solidity.Address,
        transaction: SafeTransaction,
        signatures: Map<Solidity.Address, Signature>,
        senderIsOwner: Boolean,
        txGas: BigInteger,
        dataGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        version: SemVer,
        addToHistory: Boolean = true,
        referenceId: Long? = null
    ): Single<String>

    fun notifyReject(
        safeAddress: Solidity.Address,
        transaction: SafeTransaction,
        txGas: BigInteger,
        dataGas: BigInteger,
        gasPrice: BigInteger,
        gasToken: Solidity.Address,
        targets: Set<Solidity.Address>,
        version: SemVer
    ): Completable

    fun reject(referenceId: Long)

    data class SafeExecuteState(
        val sender: Solidity.Address,
        val requiredConfirmation: Int,
        val owners: List<Solidity.Address>,
        val nonce: BigInteger,
        val balance: BigInteger,
        val version: SemVer
    )

    data class ExecuteInformation(
        val transactionHash: String,
        val transaction: SafeTransaction,
        val sender: Solidity.Address,
        val requiredConfirmation: Int,
        val owners: List<Solidity.Address>,
        val safeVersion: SemVer,
        val gasToken: Solidity.Address,
        val gasPrice: BigInteger,
        val txGas: BigInteger,
        val dataGas: BigInteger,
        val operationalGas: BigInteger,
        val balance: BigInteger
    ) {
        val isOwner by lazy {
            owners.contains(sender)
        }

        fun totalGas() = operationalGas + txGas + dataGas
        fun gasCosts() = totalGas() * gasPrice
    }

    sealed class PublishStatus {
        object Unknown : PublishStatus()
        object Pending : PublishStatus()
        data class Failed(val timestamp: Long) : PublishStatus()
        data class Success(val timestamp: Long) : PublishStatus()
    }

    interface TransactionEventsCallback {
        fun onTransactionSubmitted(safeAddress: Solidity.Address, transaction: SafeTransaction, chainHash: String, referenceId: Long?)
        fun onTransactionRejected(referenceId: Long)
    }

    enum class Operation {
        CALL, DELEGATE_CALL, CREATE;

        companion object {
            fun fromInt(value: Int): Operation =
                when (value) {
                    OPERATION_INT_CALL -> CALL
                    OPERATION_INT_DELEGATE_CALL -> DELEGATE_CALL
                    OPERATION_INT_CREATE -> CREATE
                    else -> throw IllegalArgumentException()
                }
        }
    }

    companion object {
        const val OPERATION_INT_CALL = 0
        const val OPERATION_INT_DELEGATE_CALL = 1
        const val OPERATION_INT_CREATE = 2
    }
}

fun TransactionExecutionRepository.Operation.toInt() =
    when (this) {
        TransactionExecutionRepository.Operation.CALL -> TransactionExecutionRepository.OPERATION_INT_CALL
        TransactionExecutionRepository.Operation.DELEGATE_CALL -> TransactionExecutionRepository.OPERATION_INT_DELEGATE_CALL
        TransactionExecutionRepository.Operation.CREATE -> TransactionExecutionRepository.OPERATION_INT_CREATE
    }
