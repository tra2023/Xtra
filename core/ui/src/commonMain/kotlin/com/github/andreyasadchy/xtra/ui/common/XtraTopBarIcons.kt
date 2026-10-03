package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * Drawables the shared [XtraTopBar] needs. They come from the host application's resources
 * (`:core:ui` ships no drawables), so the Android app provides them once at the Compose root,
 * next to [LocalXtraStrings].
 *
 * Every field is required: a missing icon would crash rather than silently render nothing.
 */
data class XtraTopBarIcons(
    val back: Painter,
    val search: Painter,
)

val LocalXtraTopBarIcons = staticCompositionLocalOf<XtraTopBarIcons> {
    error("XtraTopBarIcons not provided")
}
