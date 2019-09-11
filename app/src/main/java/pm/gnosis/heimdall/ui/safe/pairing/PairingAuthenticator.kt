package pm.gnosis.heimdall.ui.safe.pairing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitFirst
import pm.gnosis.heimdall.data.remote.models.push.PushServiceTemporaryAuthorization
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.heimdall.di.modules.ApplicationModule
import pm.gnosis.heimdall.ui.safe.helpers.RecoverSafeOwnersHelper
import pm.gnosis.model.Solidity
import timber.log.Timber
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

abstract class PairingAuthenticatorContract : ViewModel() {

    abstract val observableState: LiveData<ViewUpdate>

    abstract fun setup(safeAddress: Solidity.Address)

    abstract fun estimate()

    /**
     * @param signingSafe to indicate which key should be used for pairing. @null if a new key should be generated
     */
    abstract fun pair(payload: String)

    sealed class PairingResult {

        data class PairingError(val error: Exception) : PairingResult()

        data class PairingSuccess(val safe: Solidity.Address, val extension: Solidity.Address) : PairingResult()
    }

    sealed class ViewUpdate(open val isLoading: Boolean) {

        data class Balance(
            override val isLoading: Boolean,
            val balanceBefore: String,
            val fee: String,
            val balanceAfter: String,
            val tokenSymbol: String,
            val sufficient: Boolean
        ) : ViewUpdate(isLoading)

        data class Pairing(
            override val isLoading: Boolean,
            val pairingResult: PairingResult? = null
        ) : ViewUpdate(isLoading)
    }

}

class PairingAuthenticatorViewModel @Inject constructor(
    private val recoverSafeOwnersHelper: RecoverSafeOwnersHelper,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val tokenRepository: TokenRepository,
    private val transactionExecutionRepository: TransactionExecutionRepository,
    private val pushServiceRepository: PushServiceRepository,
    private val moshi: Moshi,
    private val appDispatcher: ApplicationModule.AppCoroutineDispatchers
) : PairingAuthenticatorContract() {

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        viewModelScope.launch {
            Timber.e(e)
        }
    }

    override val observableState: LiveData<ViewUpdate>
        get() = _state
    private val _state = MutableLiveData<ViewUpdate>()

    private lateinit var safeAddress: Solidity.Address

    override fun setup(safeAddress: Solidity.Address) {
        this.safeAddress = safeAddress
    }

    override fun estimate() {

        viewModelScope.launch(appDispatcher.background + errorHandler) {

            val paymentToken = tokenRepository.loadPaymentToken(safeAddress).await()

            val safeInfo = gnosisSafeRepository.loadInfo(safeAddress).awaitFirst()

            val owner = safeInfo.owners[0]
            val extension = Solidity.Address(BigInteger.valueOf(Long.MAX_VALUE))
            val pairingTransaction = recoverSafeOwnersHelper.buildRecoverTransaction(
                safeInfo,
                safeInfo.owners.subList(2, safeInfo.owners.size).toSet(),
                setOf(owner, extension)
            )

            val executeInfo = transactionExecutionRepository.loadExecuteInformation(safeAddress, paymentToken.address, pairingTransaction).await()
            val gasFee = executeInfo.gasCosts()

            _state.postValue(
                ViewUpdate.Balance(
                    false,
                    paymentToken.displayString(executeInfo.balance),
                    ERC20TokenWithBalance(paymentToken, gasFee).displayString(roundingMode = RoundingMode.UP),
                    paymentToken.displayString(executeInfo.balance - gasFee),
                    paymentToken.symbol,
                    executeInfo.balance > gasFee

                )
            )
        }
    }

    override fun pair(payload: String) {

        viewModelScope.launch(appDispatcher.background + errorHandler) {

            _state.postValue(
                ViewUpdate.Pairing(true)
            )

            try {

                val authorization = parseChromeExtensionPayload(payload)
                val pairResult = pushServiceRepository.pair(authorization, safeAddress).await()

                _state.postValue(
                    ViewUpdate.Pairing(false, PairingResult.PairingSuccess(pairResult.first.address, pairResult.second))
                )

            } catch (e: Exception) {
                _state.postValue(
                    ViewUpdate.Pairing(false, PairingResult.PairingError(e))
                )
            }
        }
    }

    private suspend fun parseChromeExtensionPayload(payload: String): PushServiceTemporaryAuthorization = coroutineScope {
        moshi.adapter(PushServiceTemporaryAuthorization::class.java).fromJson(payload)!!
    }

    companion object {
        private const val BALANCE_REQUEST_INTERVAL_SECONDS = 10L
    }
}