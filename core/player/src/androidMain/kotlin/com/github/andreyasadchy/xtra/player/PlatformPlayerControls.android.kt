package com.github.andreyasadchy.xtra.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.util.VideoQualityUtils
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

        @OptIn(UnstableApi::class)
        override fun videoRenditions(): List<VideoQuality>? {
            val manifest = exoPlayer?.currentManifest as? HlsManifest ?: return null
            val playlist = manifest.multivariantPlaylist
            return playlist.variants.mapNotNull { variant ->
                val name = variant.stableVariantId?.takeIf { it.isNotBlank() }
                    ?: playlist.videos.find { it.groupId == variant.videoGroupId }?.name?.takeIf { it.isNotBlank() }
                if (name != null) {
                    VideoQuality(
                        name = name,
                        resolution = variant.format.height,
                        frameRate = variant.format.frameRate,
                        bitrate = variant.format.bitrate,
                        codecs = variant.format.codecs,
                        url = variant.url.toString(),
                    )
                } else {
                    null
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun selectRendition(quality: VideoQuality) {
            val exo = exoPlayer ?: return
            if (quality.name == VideoQuality.AUTO_QUALITY) {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().apply {
                    clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                }.build()
                return
            }
            if (exo.currentTracks.isEmpty) {
                return
            }
            exo.currentTracks.groups.find { it.type == C.TRACK_TYPE_VIDEO }?.let { trackGroup ->
                if (trackGroup.mediaTrackGroup.length > 0) {
                    val tracks = (0 until trackGroup.mediaTrackGroup.length).map { index ->
                        val format = trackGroup.mediaTrackGroup.getFormat(index)
                        VideoQualityUtils.TrackInfo(index, format.height, format.frameRate, format.bitrate)
                    }
                    val index = VideoQualityUtils.selectTrackIndex(quality, tracks)
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().apply {
                        setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, index))
                    }.build()
                }
            }
        }
    }
