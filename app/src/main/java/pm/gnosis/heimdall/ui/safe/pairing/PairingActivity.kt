package pm.gnosis.heimdall.ui.safe.pairing

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_pairing.*
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.AccountsRepository
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.helpers.ToolbarHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.qrscan.QRCodeScanActivity
import pm.gnosis.heimdall.utils.handleQrCodeActivityResult
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.*
import timber.log.Timber
import javax.inject.Inject

abstract class PairingActivity : ViewModelActivity<PairingContract>() {
    @Inject
    lateinit var toolbarHelper: ToolbarHelper

    override fun screenId() = ScreenId.CONNECT_BROWSER_EXTENSION

    override fun inject(component: ViewComponent) = component.inject(this)

    override fun layout(): Int = R.layout.layout_pairing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        layout_pairing_title.text = getString(titleRes())
        layout_pairing_later.visible(shouldShowLaterOption())

        layout_pairing_extension_link.apply {
            val linkDrawable = ContextCompat.getDrawable(context, R.drawable.ic_external_link)!!
            linkDrawable.setBounds(0, 0, linkDrawable.intrinsicWidth, linkDrawable.intrinsicHeight)
            this.text = SpannableStringBuilder(Html.fromHtml(getString(R.string.pairing_first_step)))
                .append(" ")
                .appendText(" ", ImageSpan(linkDrawable, ImageSpan.ALIGN_BASELINE))
            setOnClickListener { shareExternalText(BuildConfig.CHROME_EXTENSION_URL, "Browser Extension URL") }
        }
    }

    override fun onStart() {
        super.onStart()

        disposables += layout_pairing_scan.clicks()
            .subscribeBy(
                onNext = { QRCodeScanActivity.startForResult(this) },
                onError = Timber::e
            )

        disposables += layout_create_safe_intro_back_arrow.clicks()
            .subscribeBy(onNext = { onBackPressed() }, onError = Timber::e)

        disposables += toolbarHelper.setupShadow(layout_pairing_toolbar_shadow, layout_pairing_content_scroll)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleQrCodeActivityResult(requestCode, resultCode, data, onQrCodeResult = { disposables += pair(it) })
    }

    private fun pair(payload: String) =
        viewModel.pair(payload, signingSafe())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { onPairingLoading(true) }
            .doFinally { onPairingLoading(false) }
            .subscribeBy(onSuccess = { (signingOwner, extension) ->
                toast(R.string.devices_paired_successfuly)
                onSuccess(signingOwner, extension)
            }, onError = {
                snackbar(layout_pairing_coordinator, R.string.error_pairing_devices)
                Timber.e(it)
            })

    private fun onPairingLoading(isLoading: Boolean) {
        layout_pairing_scan.isEnabled = !isLoading
        layout_pairing_progress_bar.visible(isLoading)
    }

    @StringRes
    abstract fun titleRes(): Int

    abstract fun onSuccess(signingOwner: AccountsRepository.SafeOwner, extension: Solidity.Address)

    abstract fun shouldShowLaterOption(): Boolean

    abstract fun signingSafe(): Solidity.Address?
}
