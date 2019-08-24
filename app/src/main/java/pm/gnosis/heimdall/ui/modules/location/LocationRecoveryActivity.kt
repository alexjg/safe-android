package pm.gnosis.heimdall.ui.modules.location


import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModel
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_location_recovery.*
import pm.gnosis.heimdall.*
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.helpers.AddressHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionActivity
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.toHexString
import java.math.BigInteger
import javax.inject.Inject

class LocationRecoveryViewModel @Inject constructor() : LocationRecoveryContract() {
    override fun createSafeTransaction(locations: List<String>, delay: Long): TransactionData {
        val initData = KeccakRecoveryModule.Setup.encode(
            Solidity.Bytes32(byteArrayOf()),
            Solidity.UInt256(delay.toBigInteger())
        )
        val proxyData = ProxyFactory.CreateProxy.encode(
            BuildConfig.LOCATION_RECOVERY_MODULE_MASTER_COPY_ADDRESS.asEthereumAddress()!!,
            Solidity.Bytes(initData.hexStringToByteArray())
        )
        val setupData = Solidity.Bytes(proxyData.hexStringToByteArray()).encode()
        val data = CreateAndAddModules.CreateAndAddModules.encode(
            BuildConfig.PROXY_FACTORY_ADDRESS.asEthereumAddress()!!,
            Solidity.Bytes(setupData.hexStringToByteArray())
        )
        return TransactionData.Generic(
            BuildConfig.CREATE_AND_ADD_MODULES_ADDRESS.asEthereumAddress()!!,
            BigInteger.ZERO,
            data,
            TransactionExecutionRepository.Operation.DELEGATE_CALL
        )
    }
}

abstract class LocationRecoveryContract : ViewModel() {
    abstract fun createSafeTransaction(locations: List<String>, delay: Long): TransactionData
}

class LocationRecoveryActivity : ViewModelActivity<LocationRecoveryContract>() {

    @Inject
    lateinit var addressHelper: AddressHelper

    override fun screenId() = ScreenId.LOCATION_RECOVERY

    override fun layout() = R.layout.layout_location_recovery

    override fun inject(component: ViewComponent) = component.inject(this)

    private val safe: Solidity.Address by lazy { intent.getStringExtra(EXTRA_SAFE_ADDRESS).asEthereumAddress()!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        location_recovery_back_arrow.setOnClickListener { onBackPressed() }
        location_recovery_submit.setOnClickListener {
            val locations = intent.getStringArrayListExtra(EXTRA_LOCATIONS)
            val delay = intent.getLongExtra(EXTRA_DELAY, 0)
            val data = viewModel.createSafeTransaction(locations, delay)
            val referenceId = intent.getLongExtra(EXTRA_REFERENCE_ID, 0)
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            startActivity(ReviewTransactionActivity.createIntent(this, safe, data, referenceId, sessionId))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        addressHelper.populateAddressInfo(location_recovery_safe_address, location_recovery_safe_name, location_recovery_safe_image, safe).forEach {
            disposables += it
        }
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"
        private const val EXTRA_LOCATIONS = "extra.string_array.locations"
        private const val EXTRA_DELAY = "extra.long.delay"
        private const val EXTRA_REFERENCE_ID = "extra.long.reference_id"
        private const val EXTRA_SESSION_ID = "extra.string.session_id"

        fun createIntent(
            context: Context,
            safe: Solidity.Address,
            locations: List<String>,
            delay: Long,
            referenceId: Long? = null,
            sessionId: String? = null
        ) =
            Intent(context, LocationRecoveryActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, safe.value.toHexString())
                putStringArrayListExtra(EXTRA_LOCATIONS, ArrayList(locations))
                putExtra(EXTRA_DELAY, delay)
                referenceId?.let { putExtra(EXTRA_REFERENCE_ID, it) }
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
    }
}
