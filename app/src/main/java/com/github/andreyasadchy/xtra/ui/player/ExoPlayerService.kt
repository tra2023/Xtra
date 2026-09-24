package com.github.andreyasadchy.xtra.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.audiofx.DynamicsProcessing
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.MediaButtonReceiver
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.VideoQualityUtils
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
class ExoPlayerService : BasePlaybackService() {

    var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var artworkUri: String? = null
    private var cachedBitmap: Bitmap? = null
    private var bitmapLoadJob: Job? = null
    private var showStreamNotificationSeekbar = true

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var sleepTimer: Timer? = null
    private var sleepTimerEndTime = 0L
    private var savePositionTimer: Timer? = null
    private var stopServiceTimer: Timer? = null

    private var videoSwapList: List<VideoSwap>? = null
    private var currentVideoSwapItem = 0
    private var videoSwapPreviousQuality: String? = null
    private var useVideoSwap = false
    private var playingAds = false
    private var checkPlaylistJob: Job? = null
    private var videoSwapActive = false
    private var videoSwapLoading = false
    private var stopVideoSwap = false
    private var hidden = false
    private var backupQualities: List<String>? = null
    private var updateQualities = false
    private var created = false

    interface Listener {
        fun started()
        fun loaded()
        fun changePlayerMode()
        fun toast(resId: Int, duration: Int)
        fun toast(text: CharSequence, duration: Int)
        fun updateVideoInfo()
    }

    var serviceListener: Listener? = null

    override fun onCreate() {
        super.onCreate()
        xtraModule = (application as XtraApp).xtraModule
    }

    private fun create(restorePauseState: Boolean) {
        if (!created) {
            created = true
            xtraModule.playbackPositionSaver.reset()
            showStreamNotificationSeekbar = prefs().getBoolean(C.PLAYER_SHOW_STREAM_NOTIFICATION_SEEKBAR, true)
            val playerListener = object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    dynamicsProcessing?.let {
                        it.release()
                        dynamicsProcessing = null
                    }
                    if (prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
                        reinitializeDynamicsProcessing(audioSessionId)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updatePlaybackState()
                    updateMetadata()
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    updateMetadata()
                    updateNotification()
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (!tracks.isEmpty) {
                        if (!loaded) {
                            loaded = true
                            serviceListener?.loaded()
                            toggleSubtitles(prefs().getBoolean(C.PLAYER_SUBTITLES_ENABLED, false))
                        }
                        if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null && quality?.name != VideoQuality.AUDIO_ONLY_QUALITY && !hidden) {
                            changeQuality(quality)
                        }
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    updatePlaybackState()
                    updateMetadata()
                    updateNotification()
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !timeline.isEmpty && qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                        updateQualities = quality?.name != VideoQuality.AUDIO_ONLY_QUALITY
                    }
                    if (qualities.isNullOrEmpty() || updateQualities) {
                        val playlist = (player?.currentManifest as? HlsManifest)?.multivariantPlaylist
                        val list = playlist?.variants?.mapNotNull { variant ->
                            val name = variant.stableVariantId?.takeIf { it.isNotBlank() }
                                ?: playlist.videos.find { it.groupId == variant.videoGroupId }?.name?.takeIf { it.isNotBlank() }
                            if (name != null) {
                                VideoQuality(name, variant.format.height, variant.format.frameRate, variant.format.bitrate, variant.format.codecs, variant.url.toString())
                            } else null
                        }
                        if (!list.isNullOrEmpty()) {
                            qualities = VideoQualityUtils.buildQualities(list, addAuto = true, addChatOnly = type == STREAM)
                            setDefaultQuality()
                            serviceListener?.changePlayerMode()
                            if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                                changeQuality(quality)
                            }
                        }
                        if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                            updateQualities = false
                        }
                    }
                    if (type == STREAM) {
                        val useVideoSwap = useVideoSwap && !stopVideoSwap
                        val hideAds = prefs().getBoolean(C.PLAYER_HIDE_ADS, false)
                        if (useVideoSwap || hideAds) {
                            val playlist = (player?.currentManifest as? HlsManifest)?.mediaPlaylist
                            val ads = playlist?.segments?.lastOrNull()?.let { segment ->
                                segment.title == "Amazon"
                                        || segment.title == "Adform"
                                        || segment.title == "DCM"
                                        ||
                                        (playlist.startTimeUs + segment.relativeStartTimeUs).let { segmentStartTime ->
                                            playlist.interstitials.find { dateRange ->
                                                (dateRange.id.startsWith("stitched-ad-")
                                                        || dateRange.clientDefinedAttributes.find { it.name == "CLASS" }?.textValue == "twitch-stitched-ad"
                                                        || dateRange.clientDefinedAttributes.find { it.name.startsWith("X-TV-TWITCH-AD-") } != null)
                                                        &&
                                                        dateRange.startDateUnixUs.let { startTime ->
                                                            (dateRange.endDateUnixUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }
                                                                ?: dateRange.durationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                                                                ?: dateRange.plannedDurationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it })?.let { endTime ->
                                                                segmentStartTime in startTime..<endTime
                                                            } == true
                                                        }
                                            } != null
                                        }
                            } == true
                            val oldValue = playingAds
                            playingAds = ads
                            if (ads) {
                                when {
                                    videoSwapActive -> {
                                        if (!videoSwapLoading) {
                                            videoSwapLoading = true
                                            currentVideoSwapItem += 1
                                            val playlist = quality?.url
                                            if (videoSwapList?.getOrNull(currentVideoSwapItem) != null && !playlist.isNullOrBlank()) {
                                                checkPlaylistJob?.cancel()
                                                checkPlaylistJob = lifecycleScope.launch {
                                                    for (i in 0 until 60) {
                                                        delay(2.seconds)
                                                        if (!xtraModule.playerRepository.checkForAds(playlist)) {
                                                            break
                                                        }
                                                    }
                                                    videoSwapLoading = true
                                                    videoSwapActive = false
                                                    if (prefs().getString(C.PLAYER_QUALITY, "720p60") != videoSwapPreviousQuality) {
                                                        prefs().edit { putString(C.PLAYER_QUALITY, videoSwapPreviousQuality) }
                                                    }
                                                    restartPlayer()
                                                    checkPlaylistJob = null
                                                }
                                            } else {
                                                videoSwapActive = false
                                                stopVideoSwap = true
                                                checkPlaylistJob?.cancel()
                                                checkPlaylistJob = null
                                            }
                                            if (prefs().getString(C.PLAYER_QUALITY, "720p60") != videoSwapPreviousQuality) {
                                                prefs().edit { putString(C.PLAYER_QUALITY, videoSwapPreviousQuality) }
                                            }
                                            restartPlayer()
                                        }
                                    }
                                    else -> {
                                        if (!oldValue) {
                                            val playlist = quality?.url
                                            when {
                                                useVideoSwap && !playlist.isNullOrBlank() -> {
                                                    if (!videoSwapLoading) {
                                                        videoSwapLoading = true
                                                        videoSwapActive = true
                                                        serviceListener?.toast(R.string.video_swap_active, Toast.LENGTH_SHORT)
                                                        checkPlaylistJob?.cancel()
                                                        checkPlaylistJob = lifecycleScope.launch {
                                                            for (i in 0 until 60) {
                                                                delay(2.seconds)
                                                                if (!xtraModule.playerRepository.checkForAds(playlist)) {
                                                                    break
                                                                }
                                                            }
                                                            videoSwapLoading = true
                                                            videoSwapActive = false
                                                            if (prefs().getString(C.PLAYER_QUALITY, "720p60") != videoSwapPreviousQuality) {
                                                                prefs().edit { putString(C.PLAYER_QUALITY, videoSwapPreviousQuality) }
                                                            }
                                                            restartPlayer()
                                                            checkPlaylistJob = null
                                                        }
                                                        videoSwapPreviousQuality = prefs().getString(C.PLAYER_QUALITY, "720p60")
                                                        restartPlayer()
                                                    }
                                                }
                                                hideAds && !hidden -> {
                                                    hidden = true
                                                    player?.let { player ->
                                                        if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                                            }.build()
                                                        }
                                                        player.volume = 0f
                                                    }
                                                    serviceListener?.toast(R.string.waiting_ads, Toast.LENGTH_LONG)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (hidden) {
                                    hidden = false
                                    player?.let { player ->
                                        if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                            }.build()
                                        }
                                        player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                    updatePlaybackState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    updatePlaybackState()
                    when (type) {
                        STREAM -> {
                            val responseCode = (player?.playerError?.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                            val isNetworkAvailable = networkCapabilities != null
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            if (isNetworkAvailable) {
                                when {
                                    responseCode == 404 -> {
                                        serviceListener?.toast(R.string.stream_ended, Toast.LENGTH_LONG)
                                    }
                                    else -> {
                                        serviceListener?.toast(R.string.player_error, Toast.LENGTH_SHORT)
                                        lifecycleScope.launch {
                                            delay(1500.milliseconds)
                                            restartPlayer()
                                        }
                                    }
                                }
                            }
                        }
                        VIDEO -> {
                            val responseCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                            val isNetworkAvailable = networkCapabilities != null
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            if (isNetworkAvailable) {
                                serviceListener?.toast(R.string.player_error, Toast.LENGTH_SHORT)
                                        lifecycleScope.launch {
                                            delay(1500.milliseconds)
                                            player?.prepare()
                                        }
                            }
                        }
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    updatePlaybackState()
                    updateNotification()
                }

                override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                    updatePlaybackState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updatePlaybackState()
                    updateNotification()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState()
                    if (isPlaying) {
                        if (savePositionTimer == null && type != STREAM) {
                            savePositionTimer = Timer().apply {
                                scheduleAtFixedRate(30000, 30000) {
                                    Handler(Looper.getMainLooper()).post {
                                        updateSavedPosition()
                                    }
                                }
                            }
                        }
                        stopServiceTimer?.cancel()
                        stopServiceTimer = null
                    } else {
                        savePositionTimer?.cancel()
                        savePositionTimer = null
                        updateSavedPosition()
                        if (stopServiceTimer == null && serviceListener == null) {
                            stopServiceTimer = Timer().apply {
                                schedule(600000) {
                                    Handler(Looper.getMainLooper()).post {
                                        stopSelf()
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    updatePlaybackState()
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    updatePlaybackState()
                }
            }
            val sessionCallback = object : MediaSession.Callback() {
                override fun onPrepare() {
                    player?.prepare()
                }

                override fun onPlay() {
                    Util.handlePlayPauseButtonAction(player)
                }

                override fun onPause() {
                    player?.pause()
                }

                override fun onSkipToNext() {
                    player?.seekForward()
                }

                override fun onSkipToPrevious() {
                    player?.seekBack()
                }

                override fun onFastForward() {
                    player?.seekForward()
                }

                override fun onRewind() {
                    player?.seekBack()
                }

                override fun onStop() {
                    player?.stop()
                }

                override fun onSeekTo(pos: Long) {
                    player?.seekTo(pos)
                }

                override fun onSetPlaybackSpeed(speed: Float) {
                    player?.setPlaybackSpeed(speed)
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    when (action) {
                        INTENT_REWIND -> player?.seekBack()
                        INTENT_FAST_FORWARD -> player?.seekForward()
                    }
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val eventHandled = super.onMediaButtonEvent(mediaButtonIntent)
                    return if (eventHandled) {
                        true
                    } else {
                        if (mediaButtonIntent.action == Intent.ACTION_MEDIA_BUTTON) {
                            val keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                        player?.seekBack()
                                        true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                        player?.seekForward()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        } else false
                    }
                }
            }
            val player = ExoPlayer.Builder(this).apply {
                setLoadControl(
                    DefaultLoadControl.Builder().apply {
                        setBufferDurationsMs(
                            prefs().getString(C.PLAYER_BUFFER_MIN, "15000")?.toIntOrNull() ?: 15000,
                            prefs().getString(C.PLAYER_BUFFER_MAX, "50000")?.toIntOrNull() ?: 50000,
                            prefs().getString(C.PLAYER_BUFFER_PLAYBACK, "2000")?.toIntOrNull() ?: 2000,
                            prefs().getString(C.PLAYER_BUFFER_REBUFFER, "2000")?.toIntOrNull() ?: 2000
                        )
                    }.build()
                )
                setAudioAttributes(AudioAttributes.DEFAULT, prefs().getBoolean(C.PLAYER_AUDIO_FOCUS, false))
                setHandleAudioBecomingNoisy(prefs().getBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true))
                setSeekBackIncrementMs((prefs().getString(C.PLAYER_REWIND, "10")?.toLongOrNull() ?: 10) * 1000)
                setSeekForwardIncrementMs((prefs().getString(C.PLAYER_FORWARD, "10")?.toLongOrNull() ?: 10) * 1000)
            }.build()
            this.player = player
            player.addListener(playerListener)
            val session = MediaSession(this, "ExoPlayerService")
            this.session = session
            session.setCallback(sessionCallback)
            try {
                session.setMediaButtonBroadcastReceiver(ComponentName(this, MediaButtonReceiver::class.java))
            } catch (e: IllegalArgumentException) {
                // https://github.com/androidx/media/issues/1730
            }
            session.isActive = true
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channelId = getString(R.string.notification_playback_channel_id)
            if (notificationManager?.getNotificationChannel(channelId) == null) {
                notificationManager?.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        ContextCompat.getString(this, R.string.notification_playback_channel_title),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            start(restorePauseState)
        }
    }

    private fun start(restorePauseState: Boolean) {
        lifecycleScope.launch {
            restorePlaybackState()
            when (type) {
                STREAM -> {
                    started = true
                    serviceListener?.started()
                    useVideoSwap = prefs().getBoolean(C.PLAYER_USE_VIDEO_SWAP, false)
                    if (useVideoSwap) {
                        videoSwapList = xtraModule.playerRepository.getVideoSwapItems().filter {
                            it.enabled && !it.platform.isNullOrBlank() && !it.playerType.isNullOrBlank()
                        }.sortedBy { it.position }
                    }
                    loadStream(restorePauseState)
                }
                VIDEO -> {
                    started = true
                    serviceListener?.started()
                    if (videoId != null) {
                        loadVideo(restorePauseState)
                        if (title == null) {
                            updateVideoInfo()
                        }
                    } else {
                        qualities?.let { list ->
                            qualities = VideoQualityUtils.buildQualities(list)
                            setDefaultQuality()
                            serviceListener?.changePlayerMode()
                            val url = quality?.url
                            if (url != null) {
                                player?.let { player ->
                                    player.setMediaSource(
                                        HlsMediaSource.Factory(
                                            DefaultDataSource.Factory(
                                                this@ExoPlayerService,
                                                OkHttpDataSource.Factory(xtraModule.okHttpClient.value)
                                            )
                                        ).createMediaSource(
                                            MediaItem.fromUri(url)
                                        )
                                    )
                                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                    player.prepare()
                                    player.playWhenReady = !restorePauseState || !paused
                                    player.seekTo(savedPosition ?: 0)
                                }
                            }
                        }
                    }
                }
                CLIP -> {
                    started = true
                    serviceListener?.started()
                    loadClip(restorePauseState)
                }
                OFFLINE_VIDEO -> {
                    offlineVideoId?.let { id ->
                        val video = xtraModule.offlineVideosRepository.getById(id)
                        if (video != null) {
                            val playbackPosition = if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                                video.lastWatchPosition
                            } else {
                                null
                            } ?: savedPosition ?: 0
                            chatUrl = video.chatUrl
                            started = true
                            serviceListener?.started()
                            if (qualities.isNullOrEmpty()) {
                                qualities = listOf(
                                    VideoQuality(VideoQuality.SOURCE_QUALITY, url = video.url),
                                    VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY),
                                )
                                setDefaultQuality()
                            }
                            serviceListener?.changePlayerMode()
                            val url = quality?.url ?: qualities?.firstOrNull()?.url
                            if (url != null) {
                                player?.let { player ->
                                    if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                        }.build()
                                    }
                                    player.setMediaItem(MediaItem.fromUri(url))
                                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                    player.prepare()
                                    player.playWhenReady = true
                                    player.seekTo(playbackPosition)
                                }
                            }
                        }
                    }
                }
                else -> stopSelf()
            }
        }
    }

    private suspend fun loadStream(restorePauseState: Boolean = false, restart: Boolean = false) {
        channelLogin?.let { channelLogin ->
            if (restart || qualities.isNullOrEmpty()) {
                val videoSwap = if (videoSwapActive) {
                    videoSwapList?.getOrNull(currentVideoSwapItem)
                } else null
                playlistUrl = getStreamPlaylistUrl(channelLogin, videoSwap = videoSwap)
            }
            videoSwapLoading = false
            val url = playlistUrl
            if (url != null) {
                player?.let { player ->
                    player.setMediaSource(
                        HlsMediaSource.Factory(
                            DefaultDataSource.Factory(
                                this@ExoPlayerService,
                                OkHttpDataSource.Factory(xtraModule.okHttpClient.value)
                            )
                        ).apply {
                            setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
                        }.createMediaSource(
                            MediaItem.Builder().apply {
                                setUri(url.toUri())
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                                setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                                    prefs().getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                                    prefs().getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                                    prefs().getString(C.PLAYER_LIVE_TARGET_OFFSET, "2000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                                }.build())
                            }.build()
                        )
                    )
                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                    player.setPlaybackSpeed(1f)
                    player.prepare()
                    player.playWhenReady = !restorePauseState || !paused
                }
            }
        }
    }

    private suspend fun getStreamPlaylistUrl(channelLogin: String, videoSwap: VideoSwap? = null): String? {
        return try {
            xtraModule.playerRepository.loadStreamPlaylistUrl(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(this, prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = channelLogin,
                platform = videoSwap?.platform ?: prefs().getString(C.TOKEN_PLATFORM, "web"),
                playerType = videoSwap?.playerType ?: prefs().getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false)
            )
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) {
                integrity.emit("refreshStream")
            }
            null
        }
    }

    private suspend fun loadVideo(restorePauseState: Boolean = false) {
        videoId?.let { videoId ->
            val playbackPosition = if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                videoId.toLongOrNull()?.let { xtraModule.playerRepository.getVideoPosition(it)?.position }
            } else {
                null
            } ?: savedPosition ?: 0
            if (qualities.isNullOrEmpty()) {
                val result = try {
                    xtraModule.playerRepository.loadVideoPlaylistUrl(
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(this@ExoPlayerService, prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                        videoId = videoId,
                        supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                        enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                } catch (e: Exception) {
                    if (e.message == C.FAILED_INTEGRITY_CHECK) {
                        integrity.emit("refreshVideo")
                    }
                    null
                }
                if (result != null) {
                    playlistUrl = result.first
                    backupQualities = result.second
                }
            }
            val url = if (skipAccessToken) {
                quality?.url
            } else {
                playlistUrl
            }
            if (url != null) {
                player?.let { player ->
                    player.setMediaSource(
                        HlsMediaSource.Factory(
                            DefaultDataSource.Factory(
                                this@ExoPlayerService,
                                OkHttpDataSource.Factory(xtraModule.okHttpClient.value)
                            )
                        ).createMediaSource(
                            MediaItem.fromUri(url)
                        )
                    )
                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                    player.prepare()
                    player.playWhenReady = !restorePauseState || !paused
                    player.seekTo(playbackPosition)
                }
            }
        }
    }

    private suspend fun updateVideoInfo() {
        val video = try {
            val response = xtraModule.graphQLRepository.loadQueryVideo(
                headers = TwitchApiHelper.getGQLHeaders(this),
                id = videoId
            )
            if (prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                    integrity.emit("refresh")
                    return
                }
            }
            response.data!!.let { item ->
                item.video?.let {
                    Video(
                        id = videoId,
                        channelId = it.owner?.id,
                        channelLogin = it.owner?.login,
                        channelName = it.owner?.displayName,
                        channelImageURL = it.owner?.profileImageURL,
                        gameId = it.game?.id,
                        gameSlug = it.game?.slug,
                        gameName = it.game?.displayName,
                        title = it.title,
                        thumbnailURL = it.previewThumbnailURL,
                        createdAt = it.createdAt?.toString(),
                        durationSeconds = it.lengthSeconds,
                        type = it.broadcastType?.toString(),
                        animatedPreviewURL = it.animatedPreviewURL,
                    )
                }
            }
        } catch (e: Exception) {
            val helixHeaders = TwitchApiHelper.getHelixHeaders(this)
            if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                try {
                    xtraModule.helixRepository.getVideos(
                        headers = helixHeaders,
                        ids = videoId?.let { listOf(it) }
                    ).data.firstOrNull()?.let {
                        Video(
                            id = it.id,
                            channelId = it.channelId,
                            channelLogin = it.channelLogin,
                            channelName = it.channelName,
                            title = it.title,
                            thumbnailURL = it.thumbnailURL,
                            createdAt = it.createdAt,
                            viewCount = it.viewCount,
                            durationSeconds = it.duration?.let { duration -> TwitchApiHelper.getDuration(duration) },
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            } else null
        }
        if (video != null) {
            channelId = video.channelId
            channelLogin = video.channelLogin
            channelName = video.channelName
            channelImage = video.channelImage
            gameId = video.gameId
            gameSlug = video.gameSlug
            gameName = video.gameName
            title = video.title
            thumbnail = video.thumbnail
            createdAt = video.createdAt
            durationSeconds = video.durationSeconds
            videoType = video.type
            videoAnimatedPreviewURL = video.animatedPreviewURL
            updateMetadata()
            updateNotification()
            serviceListener?.updateVideoInfo()
        }
    }

    private suspend fun loadClip(restorePauseState: Boolean = false) {
        clipId?.let { clipId ->
            if (qualities.isNullOrEmpty()) {
                val list = try {
                    xtraModule.playerRepository.loadClipQualities(
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(this@ExoPlayerService),
                        clipId = clipId,
                        enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false)
                    )
                } catch (e: Exception) {
                    if (e.message == C.FAILED_INTEGRITY_CHECK) {
                        integrity.emit("refreshClip")
                    }
                    null
                }
                if (list != null) {
                    val supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264")?.split(',') ?: emptyList()
                    qualities = VideoQualityUtils.buildClipQualities(list, supportedCodecs)
                    setDefaultQuality()
                }
            }
            serviceListener?.changePlayerMode()
            val url = quality?.url ?: qualities?.firstOrNull()?.url
            if (url != null) {
                player?.let { player ->
                    if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                        }.build()
                    }
                    player.setMediaSource(
                        ProgressiveMediaSource.Factory(
                            DefaultDataSource.Factory(
                                this@ExoPlayerService,
                                OkHttpDataSource.Factory(xtraModule.okHttpClient.value)
                            )
                        ).createMediaSource(
                            MediaItem.fromUri(url)
                        )
                    )
                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                    player.prepare()
                    player.playWhenReady = !restorePauseState || !paused
                    player.seekTo(savedPosition ?: 0)
                }
            }
        }
    }

    fun retry(item: String) {
        when (item) {
            "refreshStream" -> {
                lifecycleScope.launch {
                    loadStream()
                }
            }
            "refreshVideo" -> {
                lifecycleScope.launch {
                    loadVideo()
                }
            }
            "refreshClip" -> {
                lifecycleScope.launch {
                    loadClip()
                }
            }
        }
    }

    fun changeQuality(selectedQuality: VideoQuality?) {
        previousQuality = quality
        quality = selectedQuality
        quality?.let { quality ->
            player?.let { player ->
                player.currentMediaItem?.let { mediaItem ->
                    when (quality.name) {
                        VideoQuality.AUTO_QUALITY -> {
                            if (restorePlaylist) {
                                restorePlaylist = false
                                playlistUrl?.let { uri ->
                                    if (mediaItem.localConfiguration?.uri != uri.toUri()) {
                                        val position = player.currentPosition
                                        player.setMediaItem(mediaItem.buildUpon().setUri(uri).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                }
                            } else {
                                player.prepare()
                            }
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                            }.build()
                        }
                        VideoQuality.AUDIO_ONLY_QUALITY -> {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                            }.build()
                            quality.url?.let {
                                val position = player.currentPosition
                                if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                    restorePlaylist = true
                                }
                                player.setMediaItem(mediaItem.buildUpon().setUri(it).build())
                                player.prepare()
                                player.seekTo(position)
                            }
                        }
                        VideoQuality.CHAT_ONLY_QUALITY -> {
                            player.stop()
                        }
                        else -> {
                            if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                if (restorePlaylist) {
                                    restorePlaylist = false
                                    playlistUrl?.let { uri ->
                                        val position = player.currentPosition
                                        player.setMediaItem(mediaItem.buildUpon().setUri(uri).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                } else {
                                    player.prepare()
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                    if (!player.currentTracks.isEmpty) {
                                        player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }?.let { trackGroup ->
                                            if (trackGroup.mediaTrackGroup.length > 0) {
                                                val qualityResolution = quality.resolution
                                                val qualityBitrate = quality.bitrate
                                                if (qualityResolution != null) {
                                                    val formats = mutableListOf<Pair<Int, Format>>()
                                                    for (i in 0 until trackGroup.mediaTrackGroup.length) {
                                                        formats.add(i to trackGroup.mediaTrackGroup.getFormat(i))
                                                    }
                                                    val list = formats
                                                        .sortedWith(
                                                            compareByDescending<Pair<Int, Format>> { it.second.bitrate }
                                                                .thenByDescending { it.second.frameRate }
                                                                .thenByDescending { it.second.height }
                                                        )
                                                    list.find {
                                                        (qualityResolution == it.second.height
                                                                && (quality.frameRate?.let { fps -> floor(fps) } ?: 30f) >= floor(it.second.frameRate)
                                                                && (qualityBitrate == null || qualityBitrate >= it.second.bitrate))
                                                                || qualityResolution > it.second.height
                                                                || it == list.last()
                                                    }?.first?.let { index ->
                                                        setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, index))
                                                    }
                                                } else {
                                                    setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, 0))
                                                }
                                            }
                                        }
                                    }
                                }.build()
                            } else {
                                player.currentMediaItem?.let {
                                    if (it.localConfiguration?.uri?.toString() != quality.url) {
                                        val position = player.currentPosition
                                        player.setMediaItem(it.buildUpon().setUri(quality.url).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                }.build()
                            }
                        }
                    }
                    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    val cellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    if ((!cellular && prefs().getString(C.PLAYER_DEFAULT_QUALITY, "saved") == "saved") || (cellular && prefs().getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved") == "saved")) {
                        prefs().edit { putString(C.PLAYER_QUALITY, quality.name) }
                    }
                }
            }
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        player?.let { player ->
            if (enabled) {
                player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }?.let {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                        .build()
                }
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .build()
            }
        }
    }

    fun restartPlayer() {
        if (quality?.name != VideoQuality.CHAT_ONLY_QUALITY) {
            lifecycleScope.launch {
                loadStream(restart = true)
            }
        }
    }

    fun startAudioOnly() {
        player?.let { player ->
            if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                restoreQuality = true
                previousQuality = quality
                quality = qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
                quality?.let { quality ->
                    player.currentMediaItem?.let { mediaItem ->
                        if (prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                            }.build()
                        }
                        if (prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                            quality.url?.let { url ->
                                val position = player.currentPosition
                                if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                    restorePlaylist = true
                                }
                                player.setMediaItem(mediaItem.buildUpon().setUri(url).build())
                                player.prepare()
                                player.seekTo(position)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop(isInPIPMode: Boolean) {
        player?.let { player ->
            val isInteractive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
            if ((!isInPIPMode && isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO, true))
                || (!isInPIPMode && !isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_LOCKED, true))
                || (isInPIPMode && isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, false))
                || (isInPIPMode && !isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, true))) {
                if (player.playWhenReady && quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                    restoreQuality = true
                    previousQuality = quality
                    quality = qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
                    quality?.let { quality ->
                        player.currentMediaItem?.let { mediaItem ->
                            if (prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                }.build()
                            }
                            if (prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                                quality.url?.let { url ->
                                    val position = player.currentPosition
                                    if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                        restorePlaylist = true
                                    }
                                    player.setMediaItem(mediaItem.buildUpon().setUri(url).build())
                                    player.prepare()
                                    player.seekTo(position)
                                }
                            }
                        }
                    }
                }
            } else {
                player.pause()
            }
        }
    }

    private fun updatePlaybackState() {
        player?.let { player ->
            val showSeekbar = showStreamNotificationSeekbar || !player.isCurrentMediaItemLive
            session?.setPlaybackState(
                PlaybackState.Builder().apply {
                    setState(
                        when (player.playbackState) {
                            Player.STATE_IDLE -> PlaybackState.STATE_NONE
                            Player.STATE_BUFFERING -> {
                                if (Util.shouldShowPlayButton(player)) {
                                    PlaybackState.STATE_PAUSED
                                } else {
                                    PlaybackState.STATE_BUFFERING
                                }
                            }
                            Player.STATE_READY -> {
                                if (Util.shouldShowPlayButton(player)) {
                                    PlaybackState.STATE_PAUSED
                                } else {
                                    PlaybackState.STATE_PLAYING
                                }
                            }
                            Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
                            else -> PlaybackState.STATE_NONE
                        },
                        if (showSeekbar) {
                            player.currentPosition
                        } else {
                            -1
                        },
                        if (player.isPlaying && showSeekbar) {
                            player.playbackParameters.speed
                        } else {
                            0f
                        }
                    )
                    setBufferedPosition(
                        if (showSeekbar) {
                            player.bufferedPosition
                        } else {
                            -1
                        }
                    )
                    setActions(
                        (PlaybackState.ACTION_STOP
                                or PlaybackState.ACTION_PAUSE
                                or PlaybackState.ACTION_PLAY
                                or PlaybackState.ACTION_REWIND
                                or PlaybackState.ACTION_FAST_FORWARD
                                or PlaybackState.ACTION_SET_RATING
                                or PlaybackState.ACTION_PLAY_PAUSE).let {
                            if (showSeekbar) {
                                it or PlaybackState.ACTION_SEEK_TO
                            } else {
                                it
                            }.let {
                                (it or PlaybackState.ACTION_PREPARE) or PlaybackState.ACTION_SET_PLAYBACK_SPEED
                            }
                        }
                    )
                    addCustomAction(INTENT_REWIND, ContextCompat.getString(this@ExoPlayerService, R.string.rewind), androidx.media3.session.R.drawable.media3_icon_rewind)
                    addCustomAction(INTENT_FAST_FORWARD, ContextCompat.getString(this@ExoPlayerService, R.string.forward), androidx.media3.session.R.drawable.media3_icon_fast_forward)
                }.build()
            )
        }
    }

    private fun updateMetadata() {
        val url = channelImage
        val bitmap = if (!url.isNullOrBlank()) {
            if (url == artworkUri && cachedBitmap != null) {
                cachedBitmap
            } else {
                val artworkUri = url
                bitmapLoadJob?.cancel()
                bitmapLoadJob = lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val scheme = url.toUri().scheme
                        val response = if (scheme == "https" || scheme == "http") {
                            xtraModule.xtraHttpClient.getBytesOrNull(url)
                        } else {
                            FileInputStream(url).use {
                                it.readBytes()
                            }
                        }
                        if (response != null) {
                            val bitmap = BitmapFactory.decodeByteArray(response, 0, response.size)
                            if (bitmap != null) {
                                cachedBitmap = bitmap
                                withContext(Dispatchers.Main) {
                                    setMetadata(bitmap)
                                }
                            }
                        }
                    } catch (e: Exception) {

                    }
                }
                null
            }
        } else null
        setMetadata(bitmap)
    }

    private fun setMetadata(bitmap: Bitmap?) {
        player?.let { player ->
            session?.setMetadata(
                MediaMetadata.Builder().apply {
                    putText(MediaMetadata.METADATA_KEY_TITLE, title)
                    putText(MediaMetadata.METADATA_KEY_ARTIST, channelName)
                    if (bitmap != null) {
                        putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                    }
                    putLong(
                        MediaMetadata.METADATA_KEY_DURATION,
                        if (showStreamNotificationSeekbar || !player.isCurrentMediaItemLive) {
                            player.duration
                        } else {
                            -1
                        }
                    )
                }.build()
            )
        }
    }

    private fun updateNotification() {
        val url = channelImage
        val bitmap = if (!url.isNullOrBlank()) {
            if (url == artworkUri && cachedBitmap != null) {
                cachedBitmap
            } else {
                val artworkUri = url
                bitmapLoadJob?.cancel()
                bitmapLoadJob = lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val scheme = url.toUri().scheme
                        val response = if (scheme == "https" || scheme == "http") {
                            xtraModule.xtraHttpClient.getBytesOrNull(url)
                        } else {
                            FileInputStream(url).use {
                                it.readBytes()
                            }
                        }
                        if (response != null) {
                            val bitmap = BitmapFactory.decodeByteArray(response, 0, response.size)
                            if (bitmap != null) {
                                cachedBitmap = bitmap
                                withContext(Dispatchers.Main) {
                                    sendNotification(bitmap)
                                }
                            }
                        }
                    } catch (e: Exception) {

                    }
                }
                null
            }
        } else null
        sendNotification(bitmap)
    }

    private fun sendNotification(bitmap: Bitmap?) {
        player?.let { player ->
            val notification = Notification.Builder(this, getString(R.string.notification_playback_channel_id)).apply {
                setContentTitle(title)
                setContentText(channelName)
                setSmallIcon(R.drawable.notification_icon)
                if (bitmap != null) {
                    setLargeIcon(bitmap)
                }
                setGroup(GROUP_KEY)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setOngoing(false)
                setOnlyAlertOnce(true)
                if (player.isPlaying && player.playbackParameters.speed == 1f) {
                    setWhen(System.currentTimeMillis() - player.currentPosition)
                    setShowWhen(true)
                    setUsesChronometer(true)
                }
                setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(session?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                setContentIntent(
                    PendingIntent.getActivity(
                        this@ExoPlayerService,
                        REQUEST_CODE_RESUME,
                        Intent(this@ExoPlayerService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            action = MainActivity.INTENT_OPEN_PLAYER
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_rewind),
                        ContextCompat.getString(this@ExoPlayerService, R.string.rewind),
                        PendingIntent.getService(
                            this@ExoPlayerService,
                            REQUEST_CODE_REWIND,
                            Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                action = INTENT_REWIND
                            },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
                if (Util.shouldShowPlayButton(player)) {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_play),
                            ContextCompat.getString(this@ExoPlayerService, R.string.resume),
                            PendingIntent.getService(
                                this@ExoPlayerService,
                                REQUEST_CODE_PLAY_PAUSE,
                                Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                    action = INTENT_PLAY_PAUSE
                                },
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        ).build()
                    )
                } else {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_pause),
                            ContextCompat.getString(this@ExoPlayerService, R.string.pause),
                            PendingIntent.getService(
                                this@ExoPlayerService,
                                REQUEST_CODE_PLAY_PAUSE,
                                Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                    action = INTENT_PLAY_PAUSE
                                },
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        ).build()
                    )
                }
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_fast_forward),
                        ContextCompat.getString(this@ExoPlayerService, R.string.forward),
                        PendingIntent.getService(
                            this@ExoPlayerService,
                            REQUEST_CODE_FAST_FORWARD,
                            Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                action = INTENT_FAST_FORWARD
                            },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
            }.build()
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
    }

    fun setSleepTimer(duration: Long): Long {
        val endTime = sleepTimerEndTime
        sleepTimer?.cancel()
        sleepTimerEndTime = 0L
        if (duration > 0L) {
            sleepTimer = Timer().apply {
                schedule(duration) {
                    Handler(Looper.getMainLooper()).post {
                        savePosition()
                        player?.clearMediaItems()
                        player?.playWhenReady = false
                        stopSelf()
                    }
                }
            }
            sleepTimerEndTime = System.currentTimeMillis() + duration
        }
        return endTime
    }

    fun setStopServiceTimer(start: Boolean) {
        if (start) {
            if (stopServiceTimer == null && player?.isPlaying == false) {
                stopServiceTimer = Timer().apply {
                    schedule(600000) {
                        Handler(Looper.getMainLooper()).post {
                            stopSelf()
                        }
                    }
                }
            }
        } else {
            stopServiceTimer?.cancel()
            stopServiceTimer = null
        }
    }

    fun toggleDynamicsProcessing(): Boolean {
        if (dynamicsProcessing?.enabled == true) {
            dynamicsProcessing?.enabled = false
        } else {
            if (dynamicsProcessing == null) {
                player?.audioSessionId?.let { reinitializeDynamicsProcessing(it) }
            } else {
                dynamicsProcessing?.enabled = true
            }
        }
        val enabled = dynamicsProcessing?.enabled == true
        prefs().edit { putBoolean(C.PLAYER_AUDIO_COMPRESSOR, enabled) }
        return enabled
    }

    private fun reinitializeDynamicsProcessing(audioSessionId: Int) {
        dynamicsProcessing = DynamicsProcessing(0, audioSessionId, null).apply {
            for (channelIdx in 0 until channelCount) {
                for (bandIdx in 0 until getMbcByChannelIndex(channelIdx).bandCount) {
                    setMbcBandByChannelIndex(
                        channelIdx,
                        bandIdx,
                        getMbcBandByChannelIndex(channelIdx, bandIdx).apply {
                            attackTime = 0f
                            releaseTime = 0.25f
                            ratio = 1.6f
                            threshold = -50f
                            kneeWidth = 40f
                            preGain = 0f
                            postGain = 10f
                        }
                    )
                }
            }
            enabled = true
        }
    }

    private fun savePosition() {
        player?.let { player ->
            if (!player.currentTracks.isEmpty) {
                if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                    runBlocking {
                        xtraModule.playbackPositionSaver.save(type, videoId, offlineVideoId, player.currentPosition)
                    }
                }
                runBlocking {
                    xtraModule.playerRepository.deletePlaybackStates()
                }
            }
        }
    }

    private fun updateSavedPosition() {
        player?.let { player ->
            if (!player.currentTracks.isEmpty) {
                val currentPosition = player.currentPosition
                val changed = runBlocking {
                    xtraModule.playbackPositionSaver.saveIfChanged(
                        type = type,
                        videoId = videoId,
                        offlineVideoId = offlineVideoId,
                        position = currentPosition,
                        persistPosition = prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true),
                    )
                }
                if (changed) {
                    runBlocking {
                        savePlaybackState(currentPosition, !player.playWhenReady)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            INTENT_REWIND -> player?.seekBack()
            INTENT_PLAY_PAUSE -> Util.handlePlayPauseButtonAction(player)
            INTENT_FAST_FORWARD -> player?.seekForward()
            INTENT_START -> create(restorePauseState = true)
            Intent.ACTION_MEDIA_BUTTON -> create(restorePauseState = false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return ServiceBinder()
    }

    inner class ServiceBinder : Binder() {
        fun getService() = this@ExoPlayerService
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        player?.playWhenReady = false
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        session?.release()
        bitmapLoadJob?.cancel()
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val GROUP_KEY = "com.github.andreyasadchy.xtra.PLAYBACK_NOTIFICATIONS"

        private const val REQUEST_CODE_RESUME = 0
        private const val REQUEST_CODE_REWIND = 1
        private const val REQUEST_CODE_PLAY_PAUSE = 2
        private const val REQUEST_CODE_FAST_FORWARD = 3

        private const val INTENT_REWIND = "com.github.andreyasadchy.xtra.REWIND"
        private const val INTENT_PLAY_PAUSE = "com.github.andreyasadchy.xtra.PLAY_PAUSE"
        private const val INTENT_FAST_FORWARD = "com.github.andreyasadchy.xtra.FAST_FORWARD"
        const val INTENT_START = "com.github.andreyasadchy.xtra.START_PLAYBACK_SERVICE"
    }
}