package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Grid column count from the portrait/landscape preferences, shared by the
 * saved card lists (bookmarks, downloads, filters), which all duplicated the
 * same `prefs.getString(...)?.toIntOrNull() ?: 1` block in their fragments.
 */
@Composable
fun gridColumns(portrait: Boolean): Int {
    val settings = LocalXtraSettings.current
    return settings.getString(
        if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT,
        if (portrait) "1" else "2",
    )?.toIntOrNull() ?: 1
}

/** Card chrome resolved from the theme preferences. */
data class XtraCardStyle(
    val material3: Boolean,
    val cardMargin: Dp,
    val cornerRadius: Dp,
    val compactText: Boolean,
)

/**
 * Card chrome for the saved card lists, mirroring the style block triplicated
 * in the bookmarks/downloads/filters fragments.
 */
@Composable
fun rememberXtraCardStyle(): XtraCardStyle {
    val settings = LocalXtraSettings.current
    val material3 = settings.getBoolean(C.UI_THEME_MATERIAL3, true)
    return XtraCardStyle(
        material3 = material3,
        cardMargin = if (!material3) {
            0.dp
        } else if (settings.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) {
            4.dp
        } else {
            8.dp
        },
        cornerRadius = if (!material3) {
            0.dp
        } else {
            when (settings.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
                "1" -> 9.dp
                "2" -> 0.dp
                else -> 12.dp
            }
        },
        compactText = material3 && settings.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
    )
}
