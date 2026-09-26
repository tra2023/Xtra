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
import android.util.Base64
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsManifest
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.player.MediampPlaybackEngine
import com.github.andreyasadchy.xtra.player.PlayerController
import com.github.andreyasadchy.xtra.player.PlayerPrefs
import com.github.andreyasadchy.xtra.repository.HideAdsController
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.MediaButtonReceiver
import com.github.andreyasadchy.xtra.util.SleepTimer
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.m3u8.AdDetector
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayerFactory
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.togglePlayWhenReady
import java.io.FileInputStream
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android playback host. Owns the mediamp player (ExoPlayer backend), the foreground
 * media notification and the shared [PlayerController].
 */
@OptIn(UnstableApi::class)
class MediampPlayerService : BasePlaybackService() {

    var player: MediampPlayer? = null
        private set
    private lateinit var engine: MediampPlaybackEngine
    private var session: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var artworkUri: String? = null
    private var cachedBitmap: Bitmap? = null
    private var bitmapLoadJob: Job? = null
    private var showStreamNotificationSeekbar = true
    private var wasPlaying = false

    private var dynamicsProcessing: DynamicsProcessing? = null
    private lateinit var sleepTimer: SleepTimer
    private var savePositionTimer: Timer? = null
    private var stopServiceTimer: Timer? = null

    private lateinit var hideAdsController: HideAdsController
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

    private val hideAdsHost = object : HideAdsController.Host {
        override fun setVideoHidden(hidden: Boolean) {
            if (player == null) {
                return
            }
            if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                engine.setVideoEnabled(!hidden)
            }
            engine.setVolume(if (hidden) 0f else prefs().getInt(C.PLAYER_VOLUME, 100) / 100f)
        }

        override fun onWaitingAds() {
            serviceListener?.toast(R.string.waiting_ads, Toast.LENGTH_LONG)
        }
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
        override fun gqlHeaders(includeToken: Boolean): Map<String, String> = TwitchApiHelper.getGQLHeaders(this@MediampPlayerService, includeToken)

        override fun helixHeaders(): Map<String, String> = TwitchApiHelper.getHelixHeaders(this@MediampPlayerService)

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
        hideAdsController = HideAdsController(hideAdsHost)
    }

    private fun create(restorePauseState: Boolean) {
        if (created) {
            return
        }
        created = true
        xtraModule.playbackPositionSaver.reset()
        showStreamNotificationSeekbar = prefs().getBoolean(C.PLAYER_SHOW_STREAM_NOTIFICATION_SEEKBAR, true)
        val player = ExoPlayerMediampPlayerFactory().create(
            context = this,
            parentCoroutineContext = lifecycleScope.coroutineContext,
            configurePlayerBuilder = { builder ->
                builder.setLoadControl(
                    DefaultLoadControl.Builder().apply {
                        setBufferDurationsMs(
                            prefs().getString(C.PLAYER_BUFFER_MIN, "15000")?.toIntOrNull() ?: 15000,
                            prefs().getString(C.PLAYER_BUFFER_MAX, "50000")?.toIntOrNull() ?: 50000,
                            prefs().getString(C.PLAYER_BUFFER_PLAYBACK, "2000")?.toIntOrNull() ?: 2000,
                            prefs().getString(C.PLAYER_BUFFER_REBUFFER, "2000")?.toIntOrNull() ?: 2000,
                        )
                        setBackBuffer(60_000, true)
                    }.build()
                )
                builder.setAudioAttributes(AudioAttributes.DEFAULT, prefs().getBoolean(C.PLAYER_AUDIO_FOCUS, false))
                builder.setHandleAudioBecomingNoisy(prefs().getBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true))
                builder.setSeekBackIncrementMs(seekBackMs())
                builder.setSeekForwardIncrementMs(seekForwardMs())
                builder.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            },
        )
        this.player = player
        engine = MediampPlaybackEngine(player, lifecycleScope)
        playerController = PlayerController(
            session = this,
            engine = engine,
            playerRepository = xtraModule.playerRepository,
            offlineVideosRepository = xtraModule.offlineVideosRepository,
            hideAdsController = hideAdsController,
            prefs = playerPrefs,
            host = playerHost,
            scope = lifecycleScope,
        )
        observePlayer(player)
        attachExoPlayerListener(player)
        createMediaSession(player)
        createNotificationChannel()
        start(restorePauseState)
    }

    private fun observePlayer(player: MediampPlayer) {
        lifecycleScope.launch {
            player.state.collect { state ->
                updatePlaybackState()
                updateNotification()
                if (state.isPlaying != wasPlaying) {
                    wasPlaying = state.isPlaying
                    updateSaveTimer(state.isPlaying)
                }
                val ready = state.mediaStatus is MediaStatus.Ready || state.mediaStatus is MediaStatus.Ended
                if (!loaded && ready) {
                    playerController.onTracksChanged(true)
                }
            }
        }
        lifecycleScope.launch {
            player.mediaProperties.collect {
                updatePlaybackState()
                updateMetadata()
                updateNotification()
            }
        }
        lifecycleScope.launch {
            player.events.collect { event ->
                when (event) {
                    is PlaybackEvent.ErrorOccurred -> onPlayerError()
                    is PlaybackEvent.MediaEnded -> updatePlaybackState()
                    else -> {}
                }
            }
        }
    }

    /**
     * The Android mediamp backend is ExoPlayer, so its [Player.Listener] is the only way to
     * observe HLS timeline changes. This restores manifest-driven quality list updates and
     * Twitch ad detection, both of which rely on the raw HLS manifest.
     */
    private fun attachExoPlayerListener(player: MediampPlayer) {
        val exoPlayer = player.impl as? ExoPlayer ?: return
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                dynamicsProcessing?.let {
                    it.release()
                    dynamicsProcessing = null
                }
                if (prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
                    reinitializeDynamicsProcessing(audioSessionId)
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                playerController.onTimelineChanged(
                    variants = engine.videoRenditions(),
                    playlistChanged = reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
                    timelineEmpty = timeline.isEmpty,
                    sourceUpdate = reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
                    isAd = detectAd(exoPlayer.currentManifest as? HlsManifest),
                    hideAds = prefs().getBoolean(C.PLAYER_HIDE_ADS, false),
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                playerController.onTracksChanged(!tracks.isEmpty)
            }
        })
    }

    private fun detectAd(manifest: HlsManifest?): Boolean? {
        val hideAds = prefs().getBoolean(C.PLAYER_HIDE_ADS, false)
        if (type != STREAM || !hideAdsController.shouldProcess(hideAds)) {
            return null
        }
        val playlist = manifest?.mediaPlaylist ?: return false
        return playlist.segments.lastOrNull()?.let { segment ->
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
    }

    /** Raw `#EXT-...` tag lines of the current HLS manifest (debug helper). */
    fun getPlaylistTags(mediaPlaylist: Boolean): String? {
        val manifest = (player?.impl as? ExoPlayer)?.currentManifest as? HlsManifest ?: return null
        val tags = if (mediaPlaylist) {
            manifest.mediaPlaylist.tags
        } else {
            manifest.multivariantPlaylist.tags
        }
        return tags.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /**
     * Twitch marks renditions that are not part of the variant list in the
     * `com.amazon.ivs.unavailable-media` `EXT-X-SESSION-DATA` tag. The "find unlisted video"
     * flow uses them to reconstruct URLs for VODs Twitch does not list.
     */
    fun unavailableQualities(): List<VideoQuality> {
        val stableVariantIds = mutableListOf<VideoQuality>()
        val manifest = (player?.impl as? ExoPlayer)?.currentManifest as? HlsManifest ?: return stableVariantIds
        manifest.multivariantPlaylist.tags.filter { it.startsWith("#EXT-X-SESSION-DATA") }.forEach { line ->
            val id = Regex("DATA-ID=\"(.+?)\"").find(line)?.groups?.get(1)?.value
            if (id == "com.amazon.ivs.unavailable-media") {
                val value = Regex("VALUE=\"(.+?)\"").find(line)?.groups?.get(1)?.value
                if (value != null) {
                    val bytes = try {
                        Base64.decode(value, Base64.DEFAULT)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    if (bytes != null) {
                        val string = String(bytes)
                        val array = try {
                            JSONArray(string)
                        } catch (_: JSONException) {
                            null
                        }
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i) ?: continue
                                var skip = false
                                val filterReasons = obj.optJSONArray("FILTER_REASONS")
                                if (filterReasons != null) {
                                    for (filterIndex in 0 until filterReasons.length()) {
                                        val filter = filterReasons.optString(filterIndex)
                                        if (filter.isNotEmpty()) {
                                            skip = true
                                            break
                                        }
                                    }
                                }
                                if (!skip) {
                                    val newVariantId = obj.optString("STABLE-VARIANT-ID")
                                    val resolution = obj.optString("RESOLUTION")
                                    val frameRate = obj.optString("FRAME-RATE").toFloatOrNull()
                                    val bitrate = obj.optInt("BANDWIDTH")
                                    val codec = obj.optString("CODECS")
                                    if (!newVariantId.isNullOrBlank()) {
                                        stableVariantIds.add(VideoQuality(newVariantId, resolution.substringAfter('x').toIntOrNull(), frameRate, bitrate, codec))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return stableVariantIds
    }

    private fun createMediaSession(player: MediampPlayer) {
        val sessionCallback = object : MediaSession.Callback() {
            override fun onPrepare() {
                player.play()
            }

            override fun onPlay() {
                player.togglePlayWhenReady()
            }

            override fun onPause() {
                player.pause()
            }

            override fun onSkipToNext() {
                player.skip(seekForwardMs())
            }

            override fun onSkipToPrevious() {
                player.skip(-seekBackMs())
            }

            override fun onFastForward() {
                player.skip(seekForwardMs())
            }

            override fun onRewind() {
                player.skip(-seekBackMs())
            }

            override fun onStop() {
                player.stopPlayback()
            }

            override fun onSeekTo(pos: Long) {
                player.seekTo(pos)
            }

            override fun onSetPlaybackSpeed(speed: Float) {
                player.features[PlaybackSpeed]?.set(speed)
            }

            override fun onCustomAction(action: String, extras: Bundle?) {
                when (action) {
                    INTENT_REWIND -> player.skip(-seekBackMs())
                    INTENT_FAST_FORWARD -> player.skip(seekForwardMs())
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
                                    player.skip(-seekBackMs())
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    player.skip(seekForwardMs())
                                    true
                                }
                                else -> false
                            }
                        } else false
                    } else false
                }
            }
        }
        val session = MediaSession(this, "MediampPlayerService")
        this.session = session
        session.setCallback(sessionCallback)
        try {
            session.setMediaButtonBroadcastReceiver(ComponentName(this, MediaButtonReceiver::class.java))
        } catch (_: IllegalArgumentException) {
            // https://github.com/androidx/media/issues/1730
        }
        session.isActive = true
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = getString(R.string.notification_playback_channel_id)
        if (notificationManager?.getNotificationChannel(channelId) == null) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    ContextCompat.getString(this, R.string.notification_playback_channel_title),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
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

    fun playPause() {
        player?.togglePlayWhenReady()
    }

    fun rewind() {
        player?.skip(-seekBackMs())
    }

    fun fastForward() {
        player?.skip(seekForwardMs())
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun seekToLivePosition() {
        (player?.impl as? ExoPlayer)?.seekToDefaultPosition()
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.features[PlaybackSpeed]?.set(speed)
    }

    fun changeVolume(volume: Float) {
        (player?.impl as? ExoPlayer)?.volume = volume
    }

    fun getCurrentPosition(): Long? = player?.currentPositionMillis?.value

    fun getCurrentSpeed(): Float? = player?.features[PlaybackSpeed]?.value

    fun getCurrentVolume(): Float? = (player?.impl as? ExoPlayer)?.volume

    fun getTotalDuration(): Long? = player?.mediaProperties?.value?.durationMillis

    private fun seekBackMs(): Long = (prefs().getString(C.PLAYER_REWIND, "10")?.toLongOrNull() ?: 10) * 1000
    private fun seekForwardMs(): Long = (prefs().getString(C.PLAYER_FORWARD, "10")?.toLongOrNull() ?: 10) * 1000

    private fun updatePlaybackState() {
        val player = player ?: return
        val playerState = player.state.value
        val isLive = player.mediaProperties.value?.durationMillis == null
        val showSeekbar = showStreamNotificationSeekbar || !isLive
        val position = player.currentPositionMillis.value
        val speed = player.features[PlaybackSpeed]?.value ?: 1f
        session?.setPlaybackState(
            PlaybackState.Builder().apply {
                setState(
                    when (playerState.mediaStatus) {
                        MediaStatus.Idle -> PlaybackState.STATE_NONE
                        MediaStatus.Opening -> PlaybackState.STATE_BUFFERING
                        MediaStatus.Ready -> if (playerState.playWhenReady && !playerState.isBuffering) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                        MediaStatus.Ended -> PlaybackState.STATE_STOPPED
                        is MediaStatus.Error -> PlaybackState.STATE_ERROR
                        MediaStatus.Released -> PlaybackState.STATE_NONE
                    },
                    if (showSeekbar) position else -1,
                    if (playerState.isPlaying && showSeekbar) speed else 0f,
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
                addCustomAction(INTENT_REWIND, ContextCompat.getString(this@MediampPlayerService, R.string.rewind), androidx.media3.session.R.drawable.media3_icon_rewind)
                addCustomAction(INTENT_FAST_FORWARD, ContextCompat.getString(this@MediampPlayerService, R.string.forward), androidx.media3.session.R.drawable.media3_icon_fast_forward)
            }.build()
        )
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
                            FileInputStream(url).use { it.readBytes() }
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
        val duration = player?.mediaProperties?.value?.durationMillis
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
                    if (showStreamNotificationSeekbar || duration != null) {
                        duration ?: -1
                    } else {
                        -1
                    }
                )
            }.build()
        )
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
                            FileInputStream(url).use { it.readBytes() }
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
        val player = player ?: return
        val playerState = player.state.value
        val speed = player.features[PlaybackSpeed]?.value ?: 1f
        val position = player.currentPositionMillis.value
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
            if (playerState.isPlaying && speed == 1f) {
                setWhen(System.currentTimeMillis() - position)
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
                    this@MediampPlayerService,
                    REQUEST_CODE_RESUME,
                    Intent(this@MediampPlayerService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        action = MainActivity.INTENT_OPEN_PLAYER
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this@MediampPlayerService, androidx.media3.session.R.drawable.media3_icon_rewind),
                    ContextCompat.getString(this@MediampPlayerService, R.string.rewind),
                    PendingIntent.getService(
                        this@MediampPlayerService,
                        REQUEST_CODE_REWIND,
                        Intent(this@MediampPlayerService, MediampPlayerService::class.java).apply {
                            action = INTENT_REWIND
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            if (playerState.playWhenReady) {
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@MediampPlayerService, androidx.media3.session.R.drawable.media3_icon_pause),
                        ContextCompat.getString(this@MediampPlayerService, R.string.pause),
                        PendingIntent.getService(
                            this@MediampPlayerService,
                            REQUEST_CODE_PLAY_PAUSE,
                            Intent(this@MediampPlayerService, MediampPlayerService::class.java).apply {
                                action = INTENT_PLAY_PAUSE
                            },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
            } else {
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@MediampPlayerService, androidx.media3.session.R.drawable.media3_icon_play),
                        ContextCompat.getString(this@MediampPlayerService, R.string.resume),
                        PendingIntent.getService(
                            this@MediampPlayerService,
                            REQUEST_CODE_PLAY_PAUSE,
                            Intent(this@MediampPlayerService, MediampPlayerService::class.java).apply {
                                action = INTENT_PLAY_PAUSE
                            },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
            }
            addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this@MediampPlayerService, androidx.media3.session.R.drawable.media3_icon_fast_forward),
                    ContextCompat.getString(this@MediampPlayerService, R.string.forward),
                    PendingIntent.getService(
                        this@MediampPlayerService,
                        REQUEST_CODE_FAST_FORWARD,
                        Intent(this@MediampPlayerService, MediampPlayerService::class.java).apply {
                            action = INTENT_FAST_FORWARD
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
        }.build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun onPlayerError() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val isNetworkAvailable = networkCapabilities != null
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!isNetworkAvailable) {
            return
        }
        serviceListener?.toast(R.string.player_error, Toast.LENGTH_SHORT)
        lifecycleScope.launch {
            delay(1500.milliseconds)
            when (type) {
                STREAM -> restartPlayer()
                VIDEO -> playerController.retry("refreshVideo")
                CLIP -> playerController.retry("refreshClip")
            }
        }
    }

    fun setSleepTimer(duration: Long): Long {
        return sleepTimer.set(duration) {
            savePosition()
            player?.stopPlayback()
            stopSelf()
        }
    }

    fun setStopServiceTimer(start: Boolean) {
        if (start) {
            if (stopServiceTimer == null && player?.state?.value?.isPlaying == false) {
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
                (player?.impl as? ExoPlayer)?.audioSessionId?.let { reinitializeDynamicsProcessing(it) }
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
        val player = player ?: return
        if (player.mediaData.value != null) {
            if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                runBlocking {
                    xtraModule.playbackPositionSaver.save(type, videoId, offlineVideoId, player.currentPositionMillis.value)
                }
            }
            runBlocking {
                xtraModule.playerRepository.deletePlaybackStates()
            }
        }
    }

    private fun updateSaveTimer(isPlaying: Boolean) {
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

    private fun updateSavedPosition() {
        val player = player ?: return
        if (player.mediaData.value != null) {
            val currentPosition = player.currentPositionMillis.value
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
                    savePlaybackState(currentPosition, !player.state.value.playWhenReady)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            INTENT_REWIND -> player?.skip(-seekBackMs())
            INTENT_PLAY_PAUSE -> player?.togglePlayWhenReady()
            INTENT_FAST_FORWARD -> player?.skip(seekForwardMs())
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
        fun getService() = this@MediampPlayerService
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        player?.close()
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
