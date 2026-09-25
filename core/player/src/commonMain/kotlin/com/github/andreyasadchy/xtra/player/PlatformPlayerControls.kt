package com.github.andreyasadchy.xtra.player

import com.github.andreyasadchy.xtra.model.VideoQuality
import org.openani.mediamp.MediampPlayer

/**
 * Platform escape hatches that are not part of mediamp's common API.
 *
 * The ExoPlayer (Android) backend does not expose [org.openani.mediamp.features.AudioLevelController],
 * never exposes a way to disable the video track and has no video rendition selection feature, so
 * those are implemented per-platform. Platforms without support return `null` / do nothing.
 */
interface PlatformPlayerControls {
    fun setVolume(volume: Float)

    /** Enables or disables video decoding while keeping audio playing. */
    fun setVideoEnabled(enabled: Boolean)

    /**
     * The video renditions parsed from the current HLS multivariant playlist, or `null`
     * when the platform/source does not expose them (e.g. progressive clips, desktop).
     */
    fun videoRenditions(): List<VideoQuality>?

    /**
     * Selects a concrete rendition, or [VideoQuality.AUTO_QUALITY] to clear any override
     * and return to adaptive selection.
     */
    fun selectRendition(quality: VideoQuality)
}

expect fun platformPlayerControls(player: MediampPlayer): PlatformPlayerControls
