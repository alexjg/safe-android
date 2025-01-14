package pm.gnosis.heimdall.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.squareup.phrase.Phrase
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.layout_payment_tokens_item.view.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.getColorCompat

fun View.disableAccessibility() {
    ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
}

fun TextView.setFormattedText(res: Int, vararg params: Pair<String, String>) {
    text = Phrase.from(this, res).apply {
        params.forEach { (key, value) ->
            put(key, value)
        }
    }.format()
}

fun ImageView.setColorFilterCompat(@ColorRes color: Int) = setColorFilter(context.getColorCompat(color))

fun ImageView.loadTokenImage(picasso: Picasso, token: ERC20Token?) =
    when {
        token?.address == ERC20Token.ETHER_TOKEN.address -> setImageResource(R.drawable.ic_ether_symbol)
        !token?.logoUrl.isNullOrBlank() -> picasso.load(token?.logoUrl).into(this)
        else -> setImageDrawable(null)
    }


fun TextView.setCompoundDrawables(
    left: Drawable? = null,
    top: Drawable? = null,
    right: Drawable? = null,
    bottom: Drawable? = null,
    useIntrinsicBounds: Boolean = true
) =
    if (useIntrinsicBounds) setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom)
    else setCompoundDrawablesRelative(left, top, right, bottom)

fun TextView.setCompoundDrawableResource(
    @DrawableRes left: Int = 0,
    @DrawableRes top: Int = 0,
    @DrawableRes right: Int = 0,
    @DrawableRes bottom: Int = 0,
    useIntrinsicBounds: Boolean = true
) {
    setCompoundDrawables(getDrawable(left), getDrawable(top), getDrawable(right), getDrawable(bottom), useIntrinsicBounds)
}

fun View.getDrawable(@DrawableRes id: Int) = if (id == 0) null else AppCompatResources.getDrawable(context, id)

fun scaleBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    val scaleFactor = targetWidth.toFloat() / width.toFloat()
    val newHeight = (height * scaleFactor).toInt()
    return Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, true)
}

fun SwipeRefreshLayout.postIsRefreshing(refreshing: Boolean) = post { isRefreshing = refreshing }
