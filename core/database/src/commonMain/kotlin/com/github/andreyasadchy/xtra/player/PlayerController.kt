package com.github.andreyasadchy.xtra.player

import com.github.andreyasadchy.xtra.model.PlaybackType
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.repository.PlaybackSession
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.VideoSwapController
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.PlaybackUtils
import com.github.andreyasadchy.xtra.util.VideoQualityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Platform-agnostic playback orchestration: loading streams/videos/clips, quality
 * selection and background-audio handling. Drives a [PlaybackEngine] and reports
 * Android-specific actions through [Host].
 */
class PlayerController(
    private val session: PlaybackSession,
    private val engine: PlaybackEngine,
    private val playerRepository: PlayerRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val videoSwapController: VideoSwapController,
    private val prefs: PlayerPrefs,
    private val host: Host,
    private val scope: CoroutineScope,
) {
    interface Host {
        fun gqlHeaders(includeToken: Boolean): Map<String, String>
        fun helixHeaders(): Map<String, String>
        fun isCellular(): Boolean
        fun isInteractive(): Boolean
        fun onStarted()
        fun onLoaded()
        fun onPlayerModeChanged()
        fun onVideoInfoUpdated()
        fun onIntegrityError(item: String)
        fun stopService()
    }

    private var updateQualities = false

    suspend fun start(restorePauseState: Boolean) {
        when (session.type) {
            PlaybackType.STREAM -> {
                session.started = true
                host.onStarted()
                videoSwapController.init(prefs.getBoolean(C.PLAYER_USE_VIDEO_SWAP, false))
                loadStream(restorePauseState)
            }
            PlaybackType.VIDEO -> {
                session.started = true
                host.onStarted()
                if (session.videoId != null) {
                    loadVideo(restorePauseState)
                    if (session.title == null) {
                        updateVideoInfo()
                    }
                } else {
                    startVideoWithoutId(restorePauseState)
                }
            }
            PlaybackType.CLIP -> {
                session.started = true
                host.onStarted()
                loadClip(restorePauseState)
            }
            PlaybackType.OFFLINE_VIDEO -> startOfflineVideo()
            else -> host.stopService()
        }
    }

    private fun startVideoWithoutId(restorePauseState: Boolean) {
        val list = session.qualities ?: return
        session.qualities = VideoQualityUtils.buildQualities(list)
        setDefaultQuality()
        host.onPlayerModeChanged()
        val url = session.quality?.url ?: return
        engine.setSource(
            SourceRequest(
                url = url,
                format = SourceFormat.HLS,
                position = session.savedPosition ?: 0,
                playWhenReady = !restorePauseState || !session.paused,
                speed = prefs.getFloat(C.PLAYER_SPEED, 1f),
                volume = volume(),
            )
        )
    }

    private suspend fun loadStream(restorePauseState: Boolean = false, restart: Boolean = false) {
        val channelLogin = session.channelLogin ?: return
        if (restart || session.qualities.isNullOrEmpty()) {
            session.playlistUrl = getStreamPlaylistUrl(channelLogin, videoSwapController.currentSwapItemOrNull())
        }
        videoSwapController.onStreamLoaded()
        val url = session.playlistUrl ?: return
        engine.setSource(
            SourceRequest(
                url = url,
                format = SourceFormat.HLS_LIVE,
                playWhenReady = !restorePauseState || !session.paused,
                speed = 1f,
                volume = volume(),
            )
        )
    }

    private suspend fun getStreamPlaylistUrl(channelLogin: String, videoSwap: VideoSwap?): String? {
        return try {
            playerRepository.loadStreamPlaylistUrl(
                gqlHeaders = host.gqlHeaders(prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = channelLogin,
                platform = videoSwap?.platform ?: prefs.getString(C.TOKEN_PLATFORM, "web"),
                playerType = videoSwap?.playerType ?: prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) {
                host.onIntegrityError("refreshStream")
            }
            null
        }
    }

    private suspend fun loadVideo(restorePauseState: Boolean = false) {
        val videoId = session.videoId ?: return
        val playbackPosition = (if (prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
            videoId.toLongOrNull()?.let { playerRepository.getVideoPosition(it)?.position }
        } else {
            null
        }) ?: session.savedPosition ?: 0
        if (session.qualities.isNullOrEmpty()) {
            val result = try {
                playerRepository.loadVideoPlaylistUrl(
                    gqlHeaders = host.gqlHeaders(prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                    videoId = videoId,
                    supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                    enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
                )
            } catch (e: Exception) {
                if (e.message == C.FAILED_INTEGRITY_CHECK) {
                    host.onIntegrityError("refreshVideo")
                }
                null
            }
            if (result != null) {
                session.playlistUrl = result.first
            }
        }
        val url = if (session.skipAccessToken) session.quality?.url else session.playlistUrl
        if (url != null) {
            engine.setSource(
                SourceRequest(
                    url = url,
                    format = SourceFormat.HLS,
                    position = playbackPosition,
                    playWhenReady = !restorePauseState || !session.paused,
                    speed = prefs.getFloat(C.PLAYER_SPEED, 1f),
                    volume = volume(),
                )
            )
        }
    }

    suspend fun updateVideoInfo() {
        val video = try {
            playerRepository.loadVideoInfo(
                gqlHeaders = host.gqlHeaders(false),
                helixHeaders = host.helixHeaders(),
                videoId = session.videoId,
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) {
                host.onIntegrityError("refresh")
            }
            null
        }
        if (video != null) {
            session.channelId = video.channelId
            session.channelLogin = video.channelLogin
            session.channelName = video.channelName
            session.channelImage = video.channelImage
            session.gameId = video.gameId
            session.gameSlug = video.gameSlug
            session.gameName = video.gameName
            session.title = video.title
            session.thumbnail = video.thumbnail
            session.createdAt = video.createdAt
            session.durationSeconds = video.durationSeconds
            session.videoType = video.type
            session.videoAnimatedPreviewURL = video.animatedPreviewURL
            host.onVideoInfoUpdated()
        }
    }

    private suspend fun loadClip(restorePauseState: Boolean = false) {
        val clipId = session.clipId ?: return
        if (session.qualities.isNullOrEmpty()) {
            val list = try {
                playerRepository.loadClipQualities(
                    gqlHeaders = host.gqlHeaders(false),
                    clipId = clipId,
                    enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
                )
            } catch (e: Exception) {
                if (e.message == C.FAILED_INTEGRITY_CHECK) {
                    host.onIntegrityError("refreshClip")
                }
                null
            }
            if (list != null) {
                val supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264")?.split(',') ?: emptyList()
                session.qualities = VideoQualityUtils.buildClipQualities(list, supportedCodecs)
                setDefaultQuality()
            }
        }
        host.onPlayerModeChanged()
        val url = session.quality?.url ?: session.qualities?.firstOrNull()?.url ?: return
        engine.setSource(
            SourceRequest(
                url = url,
                format = SourceFormat.PROGRESSIVE,
                position = session.savedPosition ?: 0,
                playWhenReady = !restorePauseState || !session.paused,
                audioOnly = session.quality?.name == VideoQuality.AUDIO_ONLY_QUALITY,
                speed = prefs.getFloat(C.PLAYER_SPEED, 1f),
                volume = volume(),
            )
        )
    }

    private suspend fun startOfflineVideo() {
        val id = session.offlineVideoId ?: return
        val video = offlineVideosRepository.getById(id) ?: return
        val playbackPosition = (if (prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
            video.lastWatchPosition
        } else {
            null
        }) ?: session.savedPosition ?: 0
        session.chatUrl = video.chatUrl
        session.started = true
        host.onStarted()
        if (session.qualities.isNullOrEmpty()) {
            session.qualities = listOf(
                VideoQuality(VideoQuality.SOURCE_QUALITY, url = video.url),
                VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY),
            )
            setDefaultQuality()
        }
        host.onPlayerModeChanged()
        val url = session.quality?.url ?: session.qualities?.firstOrNull()?.url ?: return
        engine.setSource(
            SourceRequest(
                url = url,
                format = SourceFormat.AUTO,
                position = playbackPosition,
                playWhenReady = true,
                audioOnly = session.quality?.name == VideoQuality.AUDIO_ONLY_QUALITY,
                speed = prefs.getFloat(C.PLAYER_SPEED, 1f),
                volume = volume(),
            )
        )
    }

    fun retry(item: String) {
        when (item) {
            "refreshStream" -> scope.launch { loadStream() }
            "refreshVideo" -> scope.launch { loadVideo() }
            "refreshClip" -> scope.launch { loadClip() }
        }
    }

    fun restartPlayer() {
        if (session.quality?.name != VideoQuality.CHAT_ONLY_QUALITY) {
            scope.launch { loadStream(restart = true) }
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        engine.setSubtitlesEnabled(enabled)
    }

    fun onTracksChanged(hasTracks: Boolean) {
        if (!hasTracks) {
            return
        }
        if (!session.loaded) {
            session.loaded = true
            host.onLoaded()
            toggleSubtitles(prefs.getBoolean(C.PLAYER_SUBTITLES_ENABLED, false))
        }
        if (session.qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null && session.quality?.name != VideoQuality.AUDIO_ONLY_QUALITY && !videoSwapController.isHidden) {
            changeQuality(session.quality)
        }
    }

    fun onTimelineChanged(
        variants: List<VideoQuality>?,
        playlistChanged: Boolean,
        timelineEmpty: Boolean,
        sourceUpdate: Boolean,
        isAd: Boolean?,
        hideAds: Boolean,
    ) {
        if (playlistChanged && !timelineEmpty && session.qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
            updateQualities = session.quality?.name != VideoQuality.AUDIO_ONLY_QUALITY
        }
        if (session.qualities.isNullOrEmpty() || updateQualities) {
            if (!variants.isNullOrEmpty()) {
                session.qualities = VideoQualityUtils.buildQualities(variants, addAuto = true, addChatOnly = session.type == PlaybackType.STREAM)
                setDefaultQuality()
                host.onPlayerModeChanged()
                if (session.quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                    changeQuality(session.quality)
                }
            }
            if (sourceUpdate) {
                updateQualities = false
            }
        }
        if (isAd != null) {
            videoSwapController.onAdsChanged(
                isAd = isAd,
                hideAds = hideAds,
                playbackUrl = session.quality?.url,
                isAudioOnly = session.quality?.name == VideoQuality.AUDIO_ONLY_QUALITY,
            )
        }
    }

    fun changeQuality(selectedQuality: VideoQuality?) {
        session.previousQuality = session.quality
        session.quality = selectedQuality
        val quality = selectedQuality ?: return
        when (quality.name) {
            VideoQuality.AUTO_QUALITY -> {
                if (session.restorePlaylist) {
                    session.restorePlaylist = false
                    session.playlistUrl?.let { uri ->
                        if (engine.currentUri != uri) {
                            engine.replaceUri(uri, engine.currentPosition)
                        }
                    }
                } else {
                    engine.prepare()
                }
                engine.resetVideoTracks()
            }
            VideoQuality.AUDIO_ONLY_QUALITY -> {
                engine.setVideoEnabled(false)
                quality.url?.let { url ->
                    val position = engine.currentPosition
                    if (session.qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                        session.restorePlaylist = true
                    }
                    engine.replaceUri(url, position)
                }
            }
            VideoQuality.CHAT_ONLY_QUALITY -> engine.stop()
            else -> {
                if (session.qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                    if (session.restorePlaylist) {
                        session.restorePlaylist = false
                        session.playlistUrl?.let { uri ->
                            engine.replaceUri(uri, engine.currentPosition)
                        }
                    } else {
                        engine.prepare()
                    }
                    if (engine.hasVideoTracks) {
                        engine.selectQuality(quality)
                    }
                    engine.setVideoEnabled(true)
                } else {
                    quality.url?.let { url ->
                        if (engine.currentUri != url) {
                            engine.replaceUri(url, engine.currentPosition)
                        }
                    }
                    engine.setVideoEnabled(true)
                }
            }
        }
        val cellular = host.isCellular()
        if ((!cellular && prefs.getString(C.PLAYER_DEFAULT_QUALITY, "saved") == "saved") || (cellular && prefs.getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved") == "saved")) {
            prefs.putString(C.PLAYER_QUALITY, quality.name)
        }
    }

    fun startAudioOnly() {
        if (session.quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
            switchToBackgroundAudio()
        }
    }

    fun stop(isInPipMode: Boolean) {
        val isInteractive = host.isInteractive()
        if (PlaybackUtils.shouldKeepBackgroundAudio(
                isInPipMode = isInPipMode,
                isInteractive = isInteractive,
                backgroundAudio = prefs.getBoolean(C.PLAYER_BACKGROUND_AUDIO, true),
                backgroundAudioLocked = prefs.getBoolean(C.PLAYER_BACKGROUND_AUDIO_LOCKED, true),
                backgroundAudioPipClosed = prefs.getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, false),
                backgroundAudioPipLocked = prefs.getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, true),
            )) {
            if (engine.playWhenReady && session.quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                switchToBackgroundAudio()
            }
        } else {
            engine.pause()
        }
    }

    private fun switchToBackgroundAudio() {
        session.restoreQuality = true
        session.previousQuality = session.quality
        session.quality = session.qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
        val quality = session.quality ?: return
        if (prefs.getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
            engine.setVideoEnabled(false)
        }
        if (prefs.getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
            quality.url?.let { url ->
                val position = engine.currentPosition
                if (session.qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                    session.restorePlaylist = true
                }
                engine.replaceUri(url, position)
            }
        }
    }

    private fun setDefaultQuality() {
        session.quality = VideoQualityUtils.selectDefaultQuality(
            qualities = session.qualities,
            cellular = host.isCellular(),
            defaultCellularQuality = prefs.getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved"),
            defaultQuality = prefs.getString(C.PLAYER_DEFAULT_QUALITY, "saved"),
            savedQuality = prefs.getString(C.PLAYER_QUALITY, "720p60"),
        )
    }

    private fun volume(): Float = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
}
