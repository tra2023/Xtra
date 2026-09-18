package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.andreyasadchy.xtra.settings.XtraSettings

/**
 * Platform settings injected at the Compose root. Android provides the
 * `AndroidXtraSettings` bridge (SharedPreferences), the desktop app provides
 * its JVM implementation. Lets shared UI read preferences without a Context.
 */
val LocalXtraSettings = staticCompositionLocalOf<XtraSettings> {
    error("XtraSettings not provided")
}
