package com.github.andreyasadchy.xtra.player

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import org.openani.mediamp.MediampPlayer

actual fun platformPlayerControls(player: MediampPlayer): PlatformPlayerControls =
    object : PlatformPlayerControls {
        private val exoPlayer: ExoPlayer?
            get() = player.impl as? ExoPlayer

        override fun setVolume(volume: Float) {
            exoPlayer?.volume = volume
        }

        override fun setVideoEnabled(enabled: Boolean) {
            exoPlayer?.let { exo ->
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
                }.build()
            }
        }
    }
