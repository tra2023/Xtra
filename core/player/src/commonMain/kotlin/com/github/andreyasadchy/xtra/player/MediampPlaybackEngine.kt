package com.github.andreyasadchy.xtra.player

import com.github.andreyasadchy.xtra.model.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.MediaMetadata
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.source.UriMediaData

/**
 * Shared [PlaybackEngine] implementation backed by a mediamp [MediampPlayer].
 *
 * All playback commands are issued on [scope], which is expected to use the player's
 * main dispatcher (mediamp requires transport commands on the main thread).
 *
 * Quality selection and HLS manifest introspection are intentionally not implemented:
 * those ExoPlayer-specific features are not part of mediamp's common API, so the
 * backend picks an adaptive track automatically.
 */
class MediampPlaybackEngine(
    val player: MediampPlayer,
    private val scope: CoroutineScope,
) : PlaybackEngine {

    private val controls = platformPlayerControls(player)

    override fun setSource(request: SourceRequest) {
        scope.launch {
            player.setMediaData(
                data = UriMediaData(uri = request.url),
                playWhenReady = request.playWhenReady,
                startPositionMillis = request.position ?: 0L,
            )
            player.features[PlaybackSpeed]?.set(request.speed)
            controls.setVolume(request.volume)
            controls.setVideoEnabled(!request.audioOnly)
        }
    }

    override fun replaceUri(url: String, position: Long?) {
        scope.launch {
            player.setMediaData(
                data = UriMediaData(uri = url),
                playWhenReady = player.state.value.playWhenReady,
                startPositionMillis = position ?: player.currentPositionMillis.value,
            )
        }
    }

    override fun prepare() {
        // mediamp opens the source as part of setMediaData; nothing to do.
    }

    override fun stop() {
        player.stopPlayback()
    }

    override fun pause() {
        player.pause()
    }

    override fun setVolume(volume: Float) {
        controls.setVolume(volume)
    }

    override fun setVideoEnabled(enabled: Boolean) {
        controls.setVideoEnabled(enabled)
    }

    override fun resetVideoTracks() {
        controls.selectRendition(VideoQuality(VideoQuality.AUTO_QUALITY))
        controls.setVideoEnabled(true)
    }

    override fun selectQuality(quality: VideoQuality) {
        controls.selectRendition(quality)
    }

    /** Video renditions parsed from the HLS multivariant playlist, if the platform exposes them. */
    fun videoRenditions(): List<VideoQuality>? = controls.videoRenditions()

    override fun setSubtitlesEnabled(enabled: Boolean) {
        val group = player.features[MediaMetadata]?.subtitleTracks ?: return
        if (!enabled) {
            group.select(null)
        } else {
            scope.launch {
                group.candidates.firstOrNull()?.firstOrNull()?.let { group.select(it) }
            }
        }
    }

    override val playWhenReady: Boolean
        get() = player.state.value.playWhenReady

    override val currentPosition: Long
        get() = player.currentPositionMillis.value

    override val hasVideoTracks: Boolean
        get() = player.mediaProperties.value?.videoWidth != null

    override val currentUri: String?
        get() = (player.mediaData.value as? UriMediaData)?.uri
}
