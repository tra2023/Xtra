package com.github.andreyasadchy.xtra.ui.common

import android.app.Activity
import android.widget.LinearLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R

/**
 * Bottom padding for a Compose screen hosted by the main activity: the system
 * bar inset, but only while the app draws no bottom bar of its own.
 *
 * Read inside the composition on purpose. Installing a `ViewCompat` inset
 * listener on the root `ComposeView` (as the older Compose list screens do)
 * replaces the listener Compose needs to feed window insets into the
 * composition, which would silently drop a `Scaffold`'s status-bar padding.
 */
@Composable
fun xtraBottomInset(activity: Activity?): Dp =
    if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == true) {
        0.dp
    } else {
        with(LocalDensity.current) { WindowInsets.systemBars.getBottom(this).toDp() }
    }
