package pm.gnosis.heimdall.ui.nfc

import android.app.Dialog
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.dialog_nfc_pairing.*
import kotlinx.android.synthetic.main.dialog_nfc_pairing.view.*
import kotlinx.android.synthetic.main.dialog_nfc_signing.*
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.reporting.Event
import pm.gnosis.heimdall.reporting.EventTracker
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.dialogs.base.BaseDialog
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.heimdall.utils.CustomAlertDialogBuilder
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.*
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject

class NfcPairingDialog : BaseDialog() {
    @Inject
    lateinit var encryptionManager: EncryptionManager

    @Inject
    lateinit var eventTracker: EventTracker

    @Inject
    lateinit var viewModel: NfcContract

    private lateinit var dialogView: View
    private lateinit var alertDialog: AlertDialog
    private lateinit var adapter: NfcAdapter

    private var pairingDisposable: Disposable? = null
    private var returnValue: Solidity.Address? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setStyle(STYLE_NO_FRAME, 0)
        super.onCreate(savedInstanceState)
        inject()

        adapter = context?.let { NfcAdapter.getDefaultAdapter(it) } ?: run {
            dismiss()
            return
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_nfc_pairing, null)
        alertDialog = CustomAlertDialogBuilder.build(context!!, "Pair with KeyCard", dialogView, 0, null)
        return alertDialog
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    override fun onStart() {
        super.onStart()
        adapter.enableReaderMode(activity!!, viewModel.callback(), NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        eventTracker.submit(Event.ScreenView(ScreenId.INPUT_SAFE_ADDRESS)) // TODO change
        activity ?: run {
            dismiss()
            return
        }
        dialogView.dialog_nfc_pairing_button.setOnClickListener { if (returnValue == null) triggerPairing() else closeWithResult() }
        setUIState()
    }

    private fun setUIState() {
        pairingDisposable?.apply { if (!isDisposed) dispose() }
        dialogView.dialog_nfc_pairing_button.isEnabled = true
        val hasReturnValue = returnValue != null
        dialogView.dialog_nfc_pairing_error.text =  if (hasReturnValue) "Card successfully read, remove from phone" else null
        dialogView.dialog_nfc_pairing_button.text =  if (hasReturnValue) "Finish" else "Pair KeyCard"
        dialogView.dialog_nfc_pairing_pin.isEnabled = !hasReturnValue
        dialogView.dialog_nfc_pairing_key.isEnabled = !hasReturnValue
        dialogView.dialog_nfc_pairing_label.isEnabled = !hasReturnValue
        dialogView.dialog_nfc_pairing_button.isEnabled = true
    }

    private fun triggerPairing() {
        val pin = dialogView.dialog_nfc_pairing_pin.text.toString()
        val pairingKey = dialogView.dialog_nfc_pairing_key.text.toString()
        val label = dialogView.dialog_nfc_pairing_label.text.toString()
        dialogView.dialog_nfc_pairing_pin.isEnabled = false
        dialogView.dialog_nfc_pairing_key.isEnabled = false
        dialogView.dialog_nfc_pairing_label.isEnabled = false
        dialogView.dialog_nfc_pairing_error.text = "Hold your phone to your KeyCard"
        dialogView.dialog_nfc_pairing_button.isEnabled = false
        pairingDisposable = viewModel.observeForPairing(pin, pairingKey, label)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = {
                clearPairingDisposable()
                Log.d("#####", "Paired ${it.asEthereumAddressString()}")
                returnValue = it
                setUIState()
            }, onError = { error ->
                setUIState()
                dialogView.dialog_nfc_pairing_error.text = when (error) {
                    is SimpleLocalizedException -> error.localizedMessage()
                    else -> getString(R.string.unknown_error)
                }
            })
    }

    private fun closeWithResult() {
        returnValue?.let { (context as? NfcPairingCallback)?.pairedToKeyCard(it) }
        dismiss()
    }

    override fun onStop() {
        activity?.let { adapter.disableReaderMode(it) }
        clearPairingDisposable()
        super.onStop()
    }

    private fun clearPairingDisposable() {
        pairingDisposable?.apply { if (!isDisposed) dispose() }
        pairingDisposable = null
    }

    private fun inject() {
        DaggerViewComponent.builder()
            .viewModule(ViewModule(context!!))
            .applicationComponent(HeimdallApplication[context!!])
            .build()
            .inject(this)
    }

    companion object {
        fun create() = NfcPairingDialog()
    }

    interface NfcPairingCallback {
        fun pairedToKeyCard(address: Solidity.Address)
    }
}
