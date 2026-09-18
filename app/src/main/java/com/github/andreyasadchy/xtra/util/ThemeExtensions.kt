package com.github.andreyasadchy.xtra.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.github.andreyasadchy.xtra.ui.theme.XtraThemeFlags
import com.github.andreyasadchy.xtra.ui.theme.themeFlags

fun Configuration.isNight(): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * Resolves the stored Views theme id (see `themeValues` in arrays.xml:
 * 0 = Dark, 1 = Amoled, 2 = Light, 3 = Blue,
 * 4 = Dynamic dark, 5 = Dynamic light, 6 = Dynamic amoled),
 * honoring the "follow system" setting.
 */
fun Context.getThemeId(night: Boolean = resources.configuration.isNight()): String {
    val prefs = prefs()
    return if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
        if (night) {
            prefs.getString(C.UI_THEME_DARK_ON, "0") ?: "0"
        } else {
            prefs.getString(C.UI_THEME_DARK_OFF, "2") ?: "2"
        }
    } else {
        prefs.getString(C.THEME, "0") ?: "0"
    }
}

fun Context.getThemeFlags(night: Boolean = resources.configuration.isNight()): XtraThemeFlags =
    themeFlags(getThemeId(night))

/**
 * Compose-aware theme id: recomposes on uiMode (dark/light) changes.
 * Replaces the repeated `LocalConfiguration.current` + prefs lookup blocks.
 */
@Composable
fun rememberThemeId(): String {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return context.getThemeId(configuration.isNight())
}
