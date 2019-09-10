package pm.gnosis.heimdall.ui.safe.recover.extension

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.safe.pairing.PairingAuthenticatorContract
import pm.gnosis.heimdall.ui.tokens.payment.PaymentTokensActivity
import pm.gnosis.heimdall.utils.InfoTipDialogBuilder
import pm.gnosis.heimdall.utils.underline
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_fees_error as feesError
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_fees_info as feesInfo
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_fees_value as feesValue
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_safe_balance_after_value as balanceAfterValue
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_safe_balance_before_value as balanceBeforeValue
import kotlinx.android.synthetic.main.screen_replace_extension_start.replace_extension_back_arrow as backArrow
import kotlinx.android.synthetic.main.screen_replace_extension_start.replace_extension_bottom_panel as bottomPanel
import kotlinx.android.synthetic.main.screen_replace_extension_start.replace_extension_progress_bar as progressBar
import kotlinx.android.synthetic.main.screen_replace_extension_start.replace_extension_swipe_to_refresh as swipeToRefresh

class ReplaceExtensionStartActivity : ViewModelActivity<PairingAuthenticatorContract>() {

    override fun layout() = R.layout.screen_replace_extension_start

    override fun inject(component: ViewComponent) = viewComponent().inject(this)

    override fun screenId() = ScreenId.REPLACE_BROWSER_EXTENSION_START

    private lateinit var safe: Solidity.Address

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        safe = intent.getStringExtra(EXTRA_SAFE_ADDRESS)?.asEthereumAddress() ?: run {
            finish()
            return
        }

        viewModel.setup(safe)

        bottomPanel.disabled = true

        viewModel.observableState.observe(this, Observer {

            when (it) {

                is PairingAuthenticatorContract.ViewUpdate.Balance -> {

                    if (it.sufficient) {
                        bottomPanel.disabled = false
                        feesError.visibility = View.GONE
                    } else {
                        bottomPanel.disabled = true
                        feesError.text = getString(R.string.insufficient_funds_please_add, it.tokenSymbol)
                        feesError.visibility = View.VISIBLE
                    }
                    progressBar.visibility = View.GONE
                    balanceBeforeValue.text = it.balanceBefore
                    feesValue.text = it.fee.underline()
                    balanceAfterValue.text = it.balanceAfter
                }
            }
        })

        swipeToRefresh.setOnRefreshListener {
            viewModel.estimate()
            swipeToRefresh.isRefreshing = false
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.estimate()

        backArrow.setOnClickListener {
            finish()
        }

        feesValue.setOnClickListener {
            startActivity(PaymentTokensActivity.createIntent(this, safe))
        }

        feesInfo.setOnClickListener {
            InfoTipDialogBuilder.build(this, R.layout.dialog_network_fee, R.string.ok).show()
        }

        disposables += bottomPanel.forwardClicks.subscribeBy {
            startActivity(ReplaceExtensionQrActivity.createIntent(this, safe))
        }
    }

    companion object {

        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"

        fun createIntent(context: Context, safeAddress: Solidity.Address) = Intent(context, ReplaceExtensionStartActivity::class.java).apply {
            putExtra(EXTRA_SAFE_ADDRESS, safeAddress.asEthereumAddressString())
        }
    }
}
