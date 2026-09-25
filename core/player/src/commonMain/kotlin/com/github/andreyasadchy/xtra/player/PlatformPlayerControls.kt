package com.github.andreyasadchy.xtra.player

import org.openani.mediamp.MediampPlayer

/**
 * Platform escape hatches that are not part of mediamp's common API.
 *
 * The ExoPlayer (Android) backend does not expose [org.openani.mediamp.features.AudioLevelController]
 * and never exposes a way to disable the video track, so both are implemented per-platform.
 */
interface PlatformPlayerControls {
    fun setVolume(volume: Float)

    /** Enables or disables video decoding while keeping audio playing. */
    fun setVideoEnabled(enabled: Boolean)
}

expect fun platformPlayerControls(player: MediampPlayer): PlatformPlayerControls
