package com.github.andreyasadchy.xtra.ui.collections

import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.use
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.shape.ShapeAppearanceModel

fun ViewGroup.collectionComposeView(): ComposeView = ComposeView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
}

fun <T> ComposeView.bindCollection(item: T?, content: @Composable (T) -> Unit) {
    setContent {
        CollectionTheme(context) {
            key(item) {
                if (item != null) content(item)
            }
        }
    }
}

fun Context.collectionName(name: String?, login: String?): String? =
    if (name != null && login != null && !login.equals(name, true)) {
        when (prefs().getString(C.UI_NAME_DISPLAY, "0")) {
            "0" -> "$name($login)"
            "1" -> name
            else -> login
        }
    } else name

fun Context.collectionCount(count: Int?, plural: Int): String? = count?.let {
    resources.getQuantityString(plural, it, TwitchApiHelper.formatCount(it, prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)))
}

fun Context.collectionFollowLabels(account: Boolean, local: Boolean): List<String> = listOfNotNull(
    getString(R.string.account).takeIf { account },
    getString(R.string.local).takeIf { local },
)

@Composable
private fun CollectionTheme(context: Context, content: @Composable () -> Unit) {
    val prefs = context.prefs()
    val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
        when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> prefs.getString(C.UI_THEME_DARK_ON, "0")
            else -> prefs.getString(C.UI_THEME_DARK_OFF, "2")
        }
    } else prefs.getString(C.THEME, "0")
    XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
        val colors = MaterialTheme.colorScheme
        val typography = MaterialTheme.typography
        val density = context.resources.displayMetrics.density
        val cardStyle = context.obtainStyledAttributes(intArrayOf(R.attr.cardStyle)).use { it.getResourceId(0, 0) }
        val cardAttributes = context.obtainStyledAttributes(cardStyle, intArrayOf(android.R.attr.layout_margin, androidx.cardview.R.attr.cardElevation, androidx.cardview.R.attr.cardBackgroundColor))
        val margin = cardAttributes.getDimension(0, 8f * density) / density
        val elevation = cardAttributes.getDimension(1, density) / density
        val cardColor = if (cardAttributes.hasValue(2)) Color(cardAttributes.getColor(2, 0)) else colors.surfaceContainerLow
        cardAttributes.recycle()
        val shape = ShapeAppearanceModel.builder(context, null, R.attr.cardStyle, 0).build()
        val bounds = android.graphics.RectF(0f, 0f, 100f * density, 100f * density)
        val cardShape = RoundedCornerShape(
            topStart = (shape.topLeftCornerSize.getCornerSize(bounds) / density).dp,
            topEnd = (shape.topRightCornerSize.getCornerSize(bounds) / density).dp,
            bottomEnd = (shape.bottomRightCornerSize.getCornerSize(bounds) / density).dp,
            bottomStart = (shape.bottomLeftCornerSize.getCornerSize(bounds) / density).dp,
        )
        fun color(attr: Int, fallback: Color): Color = context.obtainStyledAttributes(intArrayOf(attr)).use {
            if (it.hasValue(0)) Color(it.getColor(0, 0)) else fallback
        }
        fun textSize(attr: Int, fallback: Float): Float {
            val appearance = context.obtainStyledAttributes(intArrayOf(attr)).use { it.getResourceId(0, 0) }
            return context.obtainStyledAttributes(appearance, intArrayOf(android.R.attr.textSize)).use {
                it.getDimension(0, fallback * context.resources.displayMetrics.scaledDensity) / context.resources.displayMetrics.scaledDensity
            }
        }
        MaterialTheme(
            colorScheme = colors.copy(
                primary = color(androidx.appcompat.R.attr.colorPrimary, colors.primary),
                onSurface = color(android.R.attr.textColorPrimary, colors.onSurface),
                onSurfaceVariant = color(android.R.attr.textColorSecondary, colors.onSurfaceVariant),
            ),
            typography = typography.copy(
                titleMedium = typography.titleMedium.copy(fontSize = textSize(com.google.android.material.R.attr.textAppearanceTitleMedium, 16f).sp),
                bodyMedium = typography.bodyMedium.copy(fontSize = textSize(com.google.android.material.R.attr.textAppearanceBodyMedium, 14f).sp),
            ),
        ) {
            CompositionLocalProvider(
                LocalCollectionCardMargin provides margin.dp,
                LocalCollectionCardShape provides cardShape,
                LocalCollectionCardElevation provides elevation.dp,
                LocalCollectionCardColor provides cardColor,
                content = content,
            )
        }
    }
}
