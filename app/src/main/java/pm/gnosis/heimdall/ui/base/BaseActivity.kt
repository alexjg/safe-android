package pm.gnosis.heimdall.ui.base

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.reporting.Event
import pm.gnosis.heimdall.reporting.EventTracker
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.security.unlock.UnlockActivity
import pm.gnosis.svalinn.security.EncryptionManager
import timber.log.Timber
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var encryptionManager: EncryptionManager

    @Inject
    lateinit var eventTracker: EventTracker

    protected val disposables by lazy { CompositeDisposable() }

    private var performSecurityCheck = true

    abstract fun screenId(): ScreenId?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeimdallApplication[this].inject(this)
        screenId()?.let { eventTracker.setCurrentScreenId(this, it) }
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStart() {
        super.onStart()
        if (performSecurityCheck) {
            disposables += encryptionManager.unlocked()
                // We block the ui thread here to avoid exposing the ui before the app is unlocked
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::checkSecurity, ::handleCheckError)
        }
        screenId()?.let { eventTracker.submit(Event.ScreenView(it)) }
    }

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }

    protected fun skipSecurityCheck() {
        performSecurityCheck = false
    }

    private fun checkSecurity(unlocked: Boolean) {
        if (!unlocked) {
            startActivity(UnlockActivity.createIntent(this))
        }
    }

    private fun handleCheckError(throwable: Throwable) {
        Timber.d(throwable)
        // Show blocker screen. No auth -> no app usage
        checkSecurity(false)
    }

    protected fun viewComponent(): ViewComponent =
        DaggerViewComponent.builder()
            .applicationComponent(HeimdallApplication[this])
            .viewModule(ViewModule(this))
            .build()
}
