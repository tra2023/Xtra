package com.github.andreyasadchy.xtra.ui.player

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
import com.github.andreyasadchy.xtra.util.rememberThemeId

internal fun Fragment.playerSheetComposeView(
    inflater: LayoutInflater,
    content: @Composable (Modifier, Dp) -> Unit,
): View = ComposeView(inflater.context).apply {
    id = R.id.recyclerView
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        val configuration = LocalConfiguration.current
        val theme = rememberThemeId()
        val padding = context.obtainStyledAttributes(intArrayOf(R.attr.dialogLayoutPadding)).use {
            (it.getDimension(0, 0f) / resources.displayMetrics.density).dp
        }
        XtraTheme(
            themeId = theme,
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
