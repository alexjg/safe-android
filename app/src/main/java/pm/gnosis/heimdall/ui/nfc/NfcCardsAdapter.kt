package pm.gnosis.heimdall.ui.nfc

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_wallet_connect_sessions_item.view.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.BridgeRepository
import pm.gnosis.heimdall.di.ForView
import pm.gnosis.heimdall.di.ViewContext
import pm.gnosis.heimdall.ui.base.LifecycleAdapter
import pm.gnosis.svalinn.common.utils.visible
import timber.log.Timber
import javax.inject.Inject

@ForView
class NfcCardsAdapter @Inject constructor(
    @ViewContext private val context: Context,
    private val viewModel: NfcContract
) : LifecycleAdapter<BridgeRepository.SessionMeta, NfcCardsAdapter.SessionViewHolder>(context) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder =
        SessionViewHolder(
            viewModel,
            LayoutInflater.from(parent.context).inflate(R.layout.layout_nfc_cards_item, parent, false)
        )

    class SessionViewHolder(private val viewModel: NfcContract, itemView: View) :
        LifecycleAdapter.LifecycleViewHolder<BridgeRepository.SessionMeta>(itemView) {
        private val disposables = CompositeDisposable()
        private var current: BridgeRepository.SessionMeta? = null

        override fun bind(data: BridgeRepository.SessionMeta, payloads: List<Any>) {
            current = data
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun start() {
            disposables.clear()
            current?.let { meta ->

            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun stop() {
            disposables.clear()
        }

        override fun unbind() {
            super.unbind()
            stop()
            current = null
        }
    }
}
