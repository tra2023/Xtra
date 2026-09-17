package com.github.andreyasadchy.xtra.ui.player

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.use
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

internal fun Fragment.playerSheetComposeView(
    inflater: LayoutInflater,
    content: @Composable (Modifier, Dp) -> Unit,
): View = ComposeView(inflater.context).apply {
    id = R.id.recyclerView
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        val configuration = LocalConfiguration.current
        val prefs = context.prefs()
        val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
            if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                prefs.getString(C.UI_THEME_DARK_ON, "0") ?: "0"
            } else {
                prefs.getString(C.UI_THEME_DARK_OFF, "2") ?: "2"
            }
        } else {
            prefs.getString(C.THEME, "0") ?: "0"
        }
        val padding = context.obtainStyledAttributes(intArrayOf(R.attr.dialogLayoutPadding)).use {
            (it.getDimension(0, 0f) / resources.displayMetrics.density).dp
        }
        XtraTheme(
            darkTheme = theme != "2" && theme != "5",
            amoled = theme == "1" || theme == "6",
            blue = theme == "3",
        ) {
            Surface {
                content(
                    Modifier
                        .heightIn(max = configuration.screenHeightDp.coerceAtLeast(1).dp)
                        .nestedScroll(rememberNestedScrollInteropConnection()),
                    padding,
                )
            }
        }
    }
}
