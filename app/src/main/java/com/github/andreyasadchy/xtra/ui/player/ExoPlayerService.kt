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
import com.github.andreyasadchy.xtra.player.PlaybackEngine
import com.github.andreyasadchy.xtra.player.PlayerController
import com.github.andreyasadchy.xtra.player.PlayerPrefs
import com.github.andreyasadchy.xtra.player.SourceFormat
import com.github.andreyasadchy.xtra.player.SourceRequest
import com.github.andreyasadchy.xtra.repository.VideoSwapController
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.MediaButtonReceiver
import com.github.andreyasadchy.xtra.util.SleepTimer
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.VideoQualityUtils
import com.github.andreyasadchy.xtra.util.m3u8.AdDetector
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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class ExoPlayerService : BasePlaybackService(), PlaybackEngine {

    var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var artworkUri: String? = null
    private var cachedBitmap: Bitmap? = null
    private var bitmapLoadJob: Job? = null
    private var showStreamNotificationSeekbar = true

    private var dynamicsProcessing: DynamicsProcessing? = null
    private lateinit var sleepTimer: SleepTimer
    private var savePositionTimer: Timer? = null
    private var stopServiceTimer: Timer? = null

    private lateinit var videoSwapController: VideoSwapController
    private lateinit var playerController: PlayerController

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

    private val videoSwapHost = object : VideoSwapController.Host {
        override fun restartPlayer() = this@ExoPlayerService.restartPlayer()

        override fun setVideoHidden(hidden: Boolean) {
            player?.let { player ->
                if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, hidden)
                    }.build()
                }
                player.volume = if (hidden) 0f else prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
            }
        }

        override fun onVideoSwapActive() {
            serviceListener?.toast(R.string.video_swap_active, Toast.LENGTH_SHORT)
        }

        override fun onWaitingAds() {
            serviceListener?.toast(R.string.waiting_ads, Toast.LENGTH_LONG)
        }

        override fun savedQuality(): String? = prefs().getString(C.PLAYER_QUALITY, "720p60")

        override fun setSavedQuality(value: String?) {
            prefs().edit { putString(C.PLAYER_QUALITY, value) }
        }
    }

    private val dataSourceFactory by lazy {
        DefaultDataSource.Factory(this, OkHttpDataSource.Factory(xtraModule.okHttpClient.value))
    }

    private val playerPrefs = object : PlayerPrefs {
        override fun getBoolean(key: String, default: Boolean) = prefs().getBoolean(key, default)
        override fun getInt(key: String, default: Int) = prefs().getInt(key, default)
        override fun getFloat(key: String, default: Float) = prefs().getFloat(key, default)
        override fun getString(key: String, default: String?) = prefs().getString(key, default)
        override fun putString(key: String, value: String?) {
            prefs().edit { putString(key, value) }
        }
    }

    private val playerHost = object : PlayerController.Host {
        override fun gqlHeaders(includeToken: Boolean): Map<String, String> = TwitchApiHelper.getGQLHeaders(this@ExoPlayerService, includeToken)

        override fun helixHeaders(): Map<String, String> = TwitchApiHelper.getHelixHeaders(this@ExoPlayerService)

        override fun isCellular(): Boolean {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            return connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }

        override fun isInteractive(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

        override fun onStarted() {
            serviceListener?.started()
        }

        override fun onLoaded() {
            serviceListener?.loaded()
        }

        override fun onPlayerModeChanged() {
            serviceListener?.changePlayerMode()
        }

        override fun onVideoInfoUpdated() {
            updateMetadata()
            updateNotification()
            serviceListener?.updateVideoInfo()
        }

        override fun onIntegrityError(item: String) {
            lifecycleScope.launch { integrity.emit(item) }
        }

        override fun stopService() {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        xtraModule = (application as XtraApp).xtraModule
        sleepTimer = SleepTimer(lifecycleScope)
        videoSwapController = VideoSwapController(xtraModule.playerRepository, lifecycleScope, videoSwapHost)
        playerController = PlayerController(
            session = this,
            engine = this,
            playerRepository = xtraModule.playerRepository,
            offlineVideosRepository = xtraModule.offlineVideosRepository,
            videoSwapController = videoSwapController,
            prefs = playerPrefs,
            host = playerHost,
            scope = lifecycleScope,
        )
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
                    playerController.onTracksChanged(!tracks.isEmpty)
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    updatePlaybackState()
                    updateMetadata()
                    updateNotification()
                    val manifest = player?.currentManifest as? HlsManifest
                    val variants = manifest?.multivariantPlaylist?.let { playlist ->
                        playlist.variants.mapNotNull { variant ->
                            val name = variant.stableVariantId?.takeIf { it.isNotBlank() }
                                ?: playlist.videos.find { it.groupId == variant.videoGroupId }?.name?.takeIf { it.isNotBlank() }
                            if (name != null) {
                                VideoQuality(name, variant.format.height, variant.format.frameRate, variant.format.bitrate, variant.format.codecs, variant.url.toString())
                            } else null
                        }
                    }
                    val hideAds = prefs().getBoolean(C.PLAYER_HIDE_ADS, false)
                    val isAd = if (type == STREAM && videoSwapController.shouldProcess(hideAds)) {
                        val playlist = manifest?.mediaPlaylist
                        playlist?.segments?.lastOrNull()?.let { segment ->
                            AdDetector.isAd(
                                segment = AdDetector.AdSegment(
                                    title = segment.title,
                                    startTimeMs = (playlist.startTimeUs + segment.relativeStartTimeUs) / 1000,
                                ),
                                ranges = playlist.interstitials.map { dateRange ->
                                    AdDetector.AdRange(
                                        id = dateRange.id,
                                        rangeClass = dateRange.clientDefinedAttributes.find { it.name == "CLASS" }?.textValue,
                                        ad = dateRange.clientDefinedAttributes.find { it.name.startsWith("X-TV-TWITCH-AD-") } != null,
                                        startTimeMs = dateRange.startDateUnixUs / 1000,
                                        endTimeMs = (dateRange.endDateUnixUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }
                                            ?: dateRange.durationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { dateRange.startDateUnixUs + it }
                                            ?: dateRange.plannedDurationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { dateRange.startDateUnixUs + it })?.let { it / 1000 },
                                    )
                                },
                            )
                        } ?: false
                    } else null
                    playerController.onTimelineChanged(
                        variants = variants,
                        playlistChanged = reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
                        timelineEmpty = timeline.isEmpty,
                        sourceUpdate = reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
                        isAd = isAd,
                        hideAds = hideAds,
                    )
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
            } catch (_: IllegalArgumentException) {
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
            playerController.start(restorePauseState)
        }
    }

    fun retry(item: String) = playerController.retry(item)

    fun changeQuality(selectedQuality: VideoQuality?) = playerController.changeQuality(selectedQuality)

    fun toggleSubtitles(enabled: Boolean) = playerController.toggleSubtitles(enabled)

    fun restartPlayer() = playerController.restartPlayer()

    fun startAudioOnly() = playerController.startAudioOnly()

    fun stop(isInPIPMode: Boolean) = playerController.stop(isInPIPMode)

    override fun setSource(request: SourceRequest) {
        val player = player ?: return
        when (request.format) {
            SourceFormat.HLS -> player.setMediaSource(
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(request.url))
            )
            SourceFormat.HLS_LIVE -> player.setMediaSource(
                HlsMediaSource.Factory(dataSourceFactory).apply {
                    setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
                }.createMediaSource(
                    MediaItem.Builder().apply {
                        setUri(request.url.toUri())
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                        setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                            prefs().getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                            prefs().getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                            prefs().getString(C.PLAYER_LIVE_TARGET_OFFSET, "2000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                        }.build())
                    }.build()
                )
            )
            SourceFormat.PROGRESSIVE -> player.setMediaSource(
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(request.url))
            )
            SourceFormat.AUTO -> player.setMediaItem(MediaItem.fromUri(request.url))
        }
        if (request.audioOnly) {
            setVideoEnabled(false)
        }
        player.volume = request.volume
        player.setPlaybackSpeed(request.speed)
        player.prepare()
        player.playWhenReady = request.playWhenReady
        request.position?.let { player.seekTo(it) }
    }

    override fun replaceUri(url: String, position: Long?) {
        val player = player ?: return
        val mediaItem = player.currentMediaItem ?: return
        player.setMediaItem(mediaItem.buildUpon().setUri(url).build())
        player.prepare()
        if (position != null) {
            player.seekTo(position)
        }
    }

    override fun prepare() {
        player?.prepare()
    }

    override fun stop() {
        player?.stop()
    }

    override fun pause() {
        player?.pause()
    }

    override fun setVolume(volume: Float) {
        player?.volume = volume
    }

    override fun setVideoEnabled(enabled: Boolean) {
        player?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, !enabled)
            }.build()
        }
    }

    override fun resetVideoTracks() {
        player?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            }.build()
        }
    }

    override fun selectQuality(quality: VideoQuality) {
        val player = player ?: return
        if (player.currentTracks.isEmpty) {
            return
        }
        player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }?.let { trackGroup ->
            if (trackGroup.mediaTrackGroup.length > 0) {
                val tracks = (0 until trackGroup.mediaTrackGroup.length).map { index ->
                    val format = trackGroup.mediaTrackGroup.getFormat(index)
                    VideoQualityUtils.TrackInfo(index, format.height, format.frameRate, format.bitrate)
                }
                val index = VideoQualityUtils.selectTrackIndex(quality, tracks)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, index))
                }.build()
            }
        }
    }

    override fun setSubtitlesEnabled(enabled: Boolean) {
        val player = player ?: return
        if (enabled) {
            player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }?.let {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                    .build()
            }
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                .build()
        }
    }

    override val playWhenReady: Boolean get() = player?.playWhenReady == true

    override val currentPosition: Long get() = player?.currentPosition ?: 0L

    override val hasVideoTracks: Boolean get() = player?.currentTracks?.isEmpty == false

    override val currentUri: String? get() = player?.currentMediaItem?.localConfiguration?.uri?.toString()

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
                                or PlaybackState.ACTION_PLAY_PAUSE).let { it ->
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
                    } catch (_: Exception) {

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
                    } catch (_: Exception) {

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
        return sleepTimer.set(duration) {
            savePosition()
            player?.clearMediaItems()
            player?.playWhenReady = false
            stopSelf()
        }
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