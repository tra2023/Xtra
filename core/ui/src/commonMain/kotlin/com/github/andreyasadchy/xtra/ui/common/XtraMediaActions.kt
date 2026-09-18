package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Video

/**
 * Platform actions the shared list rows trigger. Android forwards to
 * `MainActivity` (player) and the system share chooser; the desktop app
 * supplies its own window/navigation implementation.
 */
interface XtraMediaActions {
    fun openClip(clip: Clip)
    fun openVideo(video: Video, offset: Long?, ignoreSavedPosition: Boolean)
    fun share(url: String, title: String?)
}

val LocalXtraMediaActions = staticCompositionLocalOf<XtraMediaActions> {
    error("XtraMediaActions not provided")
}
