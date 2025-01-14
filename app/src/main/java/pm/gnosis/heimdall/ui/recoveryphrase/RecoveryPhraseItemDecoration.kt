package pm.gnosis.heimdall.ui.recoveryphrase

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.annotation.DimenRes
import androidx.recyclerview.widget.RecyclerView

class RecoveryPhraseItemDecoration(private val horizontalOffset: Int, private val verticalOffset: Int) : RecyclerView.ItemDecoration() {
    constructor(context: Context, @DimenRes horizontalOffset: Int, @DimenRes verticalOffset: Int) :
            this(context.resources.getDimensionPixelSize(horizontalOffset), context.resources.getDimensionPixelSize(verticalOffset))

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.set(horizontalOffset, verticalOffset, horizontalOffset, verticalOffset)
    }
}
