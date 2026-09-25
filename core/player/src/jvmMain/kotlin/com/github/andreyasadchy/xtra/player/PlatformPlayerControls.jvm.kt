package com.github.andreyasadchy.xtra.player

import com.github.andreyasadchy.xtra.model.VideoQuality
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

        override fun videoRenditions(): List<VideoQuality>? {
            // MPV rendition selection is a different API and is not wired up yet.
            return null
        }

        override fun selectRendition(quality: VideoQuality) {
            // No-op on desktop.
        }
    }
