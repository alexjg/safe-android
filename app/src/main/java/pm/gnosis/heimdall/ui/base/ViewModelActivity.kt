package pm.gnosis.heimdall.ui.base

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.lifecycle.ViewModel
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.svalinn.common.utils.getColorCompat
import javax.inject.Inject

abstract class ViewModelActivity<VM : ViewModel> : BaseActivity() {
    @Inject
    lateinit var viewModel: VM

    @LayoutRes
    abstract fun layout(): Int

    abstract fun inject(component: ViewComponent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject(viewComponent())
        setContentView(layout())
    }

    protected fun colorStatusBar(@ColorRes color: Int = R.color.primary) {
        if (Build.VERSION.SDK_INT >= 21) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = getColorCompat(color)
        }
    }
}
