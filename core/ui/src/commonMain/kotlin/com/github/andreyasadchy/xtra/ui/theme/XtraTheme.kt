package com.github.andreyasadchy.xtra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mirrors app colors.xml (values/colors.xml) + Base*Theme styles so Compose
// screens match the Views theme the user picked in Settings.
private val PrimaryBlue = Color(0xFFB5C4FF)
private val Accent = Color(0xFF007DCA)

private val AmoledSurfaceContainer = Color(0xFF0C0C0C)

private val BlueBackground = Color(0xFF001446)
private val BlueContainerLowest = Color(0xFF000D35)
private val BlueContainerLow = Color(0xFF00174C)
private val BlueContainer = Color(0xFF002063)
private val BlueContainerHigh = Color(0xFF032978)
private val BlueContainerHighest = Color(0xFF163584)

/**
 * Flags driving [XtraTheme]. Destructurable, so
 * `val (darkTheme, amoled, blue) = themeFlags(id)` keeps working
 * as a drop-in replacement for the old `Triple<Boolean, Boolean, Boolean>`.
 */
data class XtraThemeFlags(
    val darkTheme: Boolean,
    val amoled: Boolean,
    val blue: Boolean,
)

/**
 * Single source of truth mapping a stored Views theme id onto Compose flags.
 *
 * Ids (see `themeValues` in `app/.../res/values/arrays.xml`):
 * 0 = Dark, 1 = Amoled, 2 = Light, 3 = Blue,
 * 4 = Dynamic dark, 5 = Dynamic light, 6 = Dynamic amoled.
 */
fun themeFlags(themeId: String?): XtraThemeFlags = XtraThemeFlags(
    darkTheme = themeId != "2" && themeId != "5",
    amoled = themeId == "1" || themeId == "6",
    blue = themeId == "3",
)

/**
 * Shared Xtra theme for Android and JVM desktop.
 *
 * Covers the static Views themes (Dark default, Amoled, Light, Blue). Dynamic
 * color (theme ids 4/5/6, `DynamicColors.applyToActivityIfAvailable`) and the
 * reduced-padding / compact-text / corner variants stay Views-only for now —
 * callers map those ids via [themeFlags].
 */
@Composable
fun XtraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    blue: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        !darkTheme -> lightColorScheme(primary = Accent)
        amoled -> darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = AmoledSurfaceContainer,
        )
        blue -> darkColorScheme(
            primary = PrimaryBlue,
            background = BlueBackground,
            surface = BlueBackground,
            surfaceContainerLowest = BlueContainerLowest,
            surfaceContainerLow = BlueContainerLow,
            surfaceContainer = BlueContainer,
            surfaceContainerHigh = BlueContainerHigh,
            surfaceContainerHighest = BlueContainerHighest,
        )
        else -> darkColorScheme(primary = PrimaryBlue)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Convenience overload: pass the stored theme id directly instead of
 * manually mapping `darkTheme = theme != "2" && ...` at every call site.
 */
@Composable
fun XtraTheme(
    themeId: String?,
    content: @Composable () -> Unit,
) {
    val flags = themeFlags(themeId)
    XtraTheme(
        darkTheme = flags.darkTheme,
        amoled = flags.amoled,
        blue = flags.blue,
        content = content,
    )
}
