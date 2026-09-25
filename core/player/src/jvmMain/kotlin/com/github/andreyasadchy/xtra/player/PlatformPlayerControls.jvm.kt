package com.github.andreyasadchy.xtra.player

import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController

actual fun platformPlayerControls(player: MediampPlayer): PlatformPlayerControls =
    object : PlatformPlayerControls {
        private val audioLevelController: AudioLevelController?
            get() = player.features[AudioLevelController]

        override fun setVolume(volume: Float) {
            audioLevelController?.setVolume(volume)
        }

        override fun setVideoEnabled(enabled: Boolean) {
            // The desktop backend has no equivalent of disabling a video track.
        }
    }
