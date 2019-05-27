package pm.gnosis.heimdall.ui.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.util.Log
import androidx.lifecycle.ViewModel
import im.status.keycard.android.NFCCardManager
import im.status.keycard.applet.*
import im.status.keycard.io.CardChannel
import im.status.keycard.io.CardListener
import io.reactivex.Observable
import org.bouncycastle.util.encoders.Hex
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.toHexString
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import im.status.keycard.applet.KeycardCommandSet
import io.reactivex.schedulers.Schedulers
import pm.gnosis.crypto.ECDSASignature
import pm.gnosis.heimdall.data.remote.models.push.PushMessage
import pm.gnosis.heimdall.data.repositories.KeyCardRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.di.ApplicationContext
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.ErrorResult
import pm.gnosis.svalinn.common.utils.doOnNextForResult
import pm.gnosis.utils.asDecimalString
import timber.log.Timber


abstract class NfcContract : ViewModel() {
    abstract fun observeForSigning(hash: String): Observable<Result<Pair<Solidity.Address, ECDSASignature>>>

    abstract fun observeForPairing(pin: String, pairingKey: String, label: String): Observable<Result<Solidity.Address>>

    abstract fun callback(): NfcAdapter.ReaderCallback
}

class NfcViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val pushServiceRepository: PushServiceRepository,
    private val keyCardRepository: KeyCardRepository
) : NfcContract(), CardListener {

    private val errorHandler = SimpleLocalizedException.networkErrorHandlerBuilder(applicationContext).build()
    private val listeners: MutableList<CardListener> = CopyOnWriteArrayList()

    override fun onConnected(channel: CardChannel?) {
        listeners.forEach { it.onConnected(channel) }
    }

    override fun onDisconnected() {
        listeners.forEach { it.onDisconnected() }
    }

    private fun addListener(listener: CardListener) =
        listeners.add(listener)

    private fun removeListener(listener: CardListener) =
        listeners.remove(listener)

    private val manager = NFCCardManager().apply {
        setCardListener(this@NfcViewModel)
        start()
    }

    override fun callback(): NfcAdapter.ReaderCallback = manager

    override fun observeForSigning(hash: String): Observable<Result<Pair<Solidity.Address, ECDSASignature>>> =
        performOnChannel { keyCardRepository.signWithKeyCard(it, hash.hexStringToByteArray()) }
            .doOnNextForResult(onNext = {
                // Small hack to propagate signature
                pushServiceRepository.handlePushMessage(PushMessage.ConfirmTransaction(
                    hash, it.second.r.asDecimalString(), it.second.s.asDecimalString(), it.second.v.toString(10)
                ))
            })

    override fun observeForPairing(pin: String, pairingKey: String, label: String): Observable<Result<Solidity.Address>> =
        performOnChannel { keyCardRepository.pairKeyCard(it, pin, pairingKey, label) }

    private fun <T> performOnChannel(action: (channel: CardChannel) -> T) : Observable<Result<T>> =
        Observable.create<Result<T>> { emitter ->
            val listener = object : CardListener {
                override fun onConnected(channel: CardChannel?) {
                    channel ?: return
                    try {
                        emitter.onNext(DataResult(action(channel)))
                    } catch (e: Exception) {
                        emitter.onNext(ErrorResult(errorHandler.translate(e)))
                    }
                }

                override fun onDisconnected() {
                    Timber.d("KeyCard disconnected.")
                }

            }
            addListener(listener)
            emitter.setCancellable { removeListener(listener) }
        }
            .subscribeOn(Schedulers.io())
}
