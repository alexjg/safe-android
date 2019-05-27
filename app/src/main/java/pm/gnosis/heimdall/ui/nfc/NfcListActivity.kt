package pm.gnosis.heimdall.ui.nfc

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.layout_nfc_cards.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.walletconnect.WalletConnectSessionsAdapter
import javax.inject.Inject

class NfcListActivity : ViewModelActivity<NfcContract>() {

    @Inject
    lateinit var adapter: NfcCardsAdapter

    override fun layout() = R.layout.layout_nfc_cards

    override fun screenId() = ScreenId.INPUT_SAFE_ADDRESS // TODO change

    override fun inject(component: ViewComponent) = component.inject(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layout_nfc_cards_recycler_view.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        layout_nfc_cards_recycler_view.adapter = adapter
    }

    override fun onStart() {
        super.onStart()

    }

}
