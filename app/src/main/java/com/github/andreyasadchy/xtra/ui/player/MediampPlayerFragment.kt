package com.github.andreyasadchy.xtra.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.player.XtraPlayerSurface
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.MediaMetadata
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio

class MediampPlayerFragment : PlayerFragment() {

    override var playbackService: MediampPlayerService? = null
    private var serviceConnection: ServiceConnection? = null
    private val updateProgressAction = Runnable { if (view != null) updateProgress() }

    private var stateJob: Job? = null
    private var metadataJob: Job? = null
    private var tracksJob: Job? = null
    private var eventsJob: Job? = null
    private var speedJob: Job? = null

    private var hasSubtitles = false
    private var subtitlesSelected = false
    private var chatReplayStarted = false

    private val serviceListener = object : MediampPlayerService.Listener {
        override fun started() {
            if (view != null) {
                if (!started) {
                    if (isInitialized || !enableNetworkCheck) {
                        started = true
                        start()
                    }
                } else {
                    chatFragment?.startReplayChatLoad()
                    if (playbackService?.restoreQuality == true) {
                        playbackService?.restoreQuality = false
                        changeQuality(playbackService?.previousQuality)
                    }
                }
            }
        }

        override fun loaded() {
            if (view != null) {
                with(binding.playerControls) {
                    quality.isEnabled = true
                    quality.setColorFilter(android.graphics.Color.WHITE)
                    download.isEnabled = true
                    download.setColorFilter(android.graphics.Color.WHITE)
                    audioOnly.isEnabled = true
                    audioOnly.setColorFilter(android.graphics.Color.WHITE)
                    setQualityText()
                }
            }
        }

        override fun changePlayerMode() {
            if (view != null) {
                this@MediampPlayerFragment.changePlayerMode()
            }
        }

        override fun toast(resId: Int, duration: Int) {
            if (view != null) {
                Toast.makeText(requireContext(), resId, duration).show()
            }
        }

        override fun toast(text: CharSequence, duration: Int) {
            if (view != null) {
                Toast.makeText(requireContext(), text, duration).show()
            }
        }

        override fun updateVideoInfo() {
            if (view != null) {
                with(binding.playerControls) {
                    val titleText = playbackService?.title
                    if (!titleText.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_TITLE, true)) {
                        title.visibility = View.VISIBLE
                        title.text = titleText
                    }
                    val gameName = playbackService?.gameName
                    if (!gameName.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_CATEGORY, true)) {
                        category.visibility = View.VISIBLE
                        category.text = gameName
                        category.setOnClickListener {
                            findNavController().navigate(
                                if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                                    GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                        gameId = playbackService?.gameId,
                                        gameSlug = playbackService?.gameSlug,
                                        gameName = gameName
                                    )
                                } else {
                                    GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                        gameId = playbackService?.gameId,
                                        gameSlug = playbackService?.gameSlug,
                                        gameName = gameName
                                    )
                                }
                            )
                            minimize()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (view == null) {
                    return
                }
                val binder = service as MediampPlayerService.ServiceBinder
                playbackService = binder.getService()
                playbackService?.serviceListener = serviceListener
                val player = playbackService?.player
                if (player != null) {
                    binding.playerComposeView.setContent {
                        XtraPlayerSurface(player, Modifier.fillMaxSize())
                    }
                    observePlayer(player)
                }
                val endTime = playbackService?.setSleepTimer(-1)
                if (endTime != null && endTime > 0L) {
                    val duration = endTime - System.currentTimeMillis()
                    if (duration > 0L) {
                        (activity as? MainActivity)?.setSleepTimer(duration)
                    } else {
                        minimize()
                        close()
                        (activity as? MainActivity)?.closePlayer()
                    }
                }
                playbackService?.setStopServiceTimer(false)
                val currentPlayer = playbackService?.player
                if (currentPlayer != null) {
                    if (!requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) && canEnterPictureInPicture()) {
                        requireView().keepScreenOn = currentPlayer.state.value.isPlaying
                    }
                    updateProgress()
                    updatePlayPauseButton(!currentPlayer.state.value.playWhenReady)
                    setPipActions(currentPlayer.state.value.isPlaying)
                }
                if (playbackService?.started == true) {
                    if (!started) {
                        if (isInitialized || !enableNetworkCheck) {
                            started = true
                            start()
                        }
                    } else {
                        chatFragment?.startReplayChatLoad()
                        if (playbackService?.restoreQuality == true) {
                            playbackService?.restoreQuality = false
                            changeQuality(playbackService?.previousQuality)
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }
        val intent = Intent(requireContext(), MediampPlayerService::class.java).apply {
            action = MediampPlayerService.INTENT_START
        }
        requireContext().startService(intent)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        serviceConnection = connection
    }

    private fun observePlayer(player: MediampPlayer) {
        stateJob = viewLifecycleOwner.lifecycleScope.launch {
            player.state.collect { state ->
                binding.bufferingIndicator.isVisible = state.isBuffering || state.mediaStatus is MediaStatus.Opening
                val showPlayButton = !state.playWhenReady
                updatePlayPauseButton(showPlayButton)
                setPipActions(!showPlayButton)
                updateProgress()
                controllerAutoHide = !showPlayButton
                if (useController) {
                    showController(show = playbackService?.type != BasePlaybackService.STREAM && state.mediaStatus is MediaStatus.Ended)
                }
                if (!requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) && canEnterPictureInPicture()) {
                    requireView().keepScreenOn = state.isPlaying
                }
                if (!chatReplayStarted && (state.mediaStatus is MediaStatus.Ready || state.mediaStatus is MediaStatus.Ended)) {
                    chatReplayStarted = true
                    chatFragment?.startReplayChatLoad()
                }
            }
        }
        metadataJob = viewLifecycleOwner.lifecycleScope.launch {
            player.mediaProperties.collect { properties ->
                val duration = properties?.durationMillis ?: 0
                binding.playerControls.progressBar.setDuration(duration)
                binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                updateProgress()
                setSubtitlesButton()
            }
        }
        eventsJob = viewLifecycleOwner.lifecycleScope.launch {
            player.events.collect { event ->
                when (event) {
                    is PlaybackEvent.SeekCompleted -> {
                        if (chatFragment?.context != null) {
                            chatFragment?.updatePosition(event.positionMillis)
                        }
                    }
                    is PlaybackEvent.MediaEnded -> {
                        updateProgress()
                    }
                    else -> {}
                }
            }
        }
        speedJob = viewLifecycleOwner.lifecycleScope.launch {
            player.features[PlaybackSpeed]?.valueFlow?.collect { speed ->
                if (chatFragment?.context != null) {
                    chatFragment?.updateSpeed(speed)
                }
            }
        }
        val subtitleTracks = player.features[MediaMetadata]?.subtitleTracks
        if (subtitleTracks != null) {
            tracksJob = viewLifecycleOwner.lifecycleScope.launch {
                launch {
                    subtitleTracks.candidates.collect { candidates ->
                        hasSubtitles = candidates.isNotEmpty()
                        setSubtitlesButton()
                    }
                }
                launch {
                    subtitleTracks.selected.collect { selected ->
                        subtitlesSelected = selected != null
                        setSubtitlesButton()
                    }
                }
            }
        }
    }

    private fun updatePlayPauseButton(showPlayButton: Boolean) {
        with(binding.playerControls) {
            if (showPlayButton) {
                playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                playPause.visibility = View.VISIBLE
            } else {
                playPause.setImageResource(R.drawable.baseline_pause_black_48)
                if (playbackService?.type == BasePlaybackService.STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                    playPause.visibility = View.GONE
                }
            }
        }
    }

    private fun cancelJobs() {
        stateJob?.cancel()
        metadataJob?.cancel()
        tracksJob?.cancel()
        eventsJob?.cancel()
        speedJob?.cancel()
        stateJob = null
        metadataJob = null
        tracksJob = null
        eventsJob = null
        speedJob = null
        chatReplayStarted = false
    }

    override fun getCurrentPosition() = playbackService?.getCurrentPosition()

    override fun getCurrentSpeed() = playbackService?.getCurrentSpeed()

    override fun getCurrentVolume() = playbackService?.getCurrentVolume()

    override fun getTotalDuration() = playbackService?.getTotalDuration()

    override fun playPause() {
        playbackService?.playPause()
    }

    override fun rewind() {
        playbackService?.rewind()
    }

    override fun fastForward() {
        playbackService?.fastForward()
    }

    override fun seek(position: Long) {
        playbackService?.seekTo(position)
    }

    override fun seekToLivePosition() {
        playbackService?.seekToLivePosition()
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackService?.setPlaybackSpeed(speed)
    }

    override fun changeVolume(volume: Float) {
        playbackService?.changeVolume(volume)
    }

    override fun applyAspectRatioMode(mode: Int) {
        val aspectRatioMode = when (mode) {
            3 -> AspectRatioMode.STRETCH
            4 -> AspectRatioMode.CROP
            else -> AspectRatioMode.FIT
        }
        playbackService?.player?.features?.get(VideoAspectRatio)?.setMode(aspectRatioMode)
    }

    override fun updateProgress() {
        with(binding.playerControls) {
            if (root.isVisible && !progressBar.isPressed) {
                val player = playbackService?.player ?: return
                val currentPosition = player.currentPositionMillis.value
                position.text = DateUtils.formatElapsedTime(currentPosition / 1000)
                progressBar.setPosition(currentPosition)
                progressBar.setBufferedPosition(0)
                root.removeCallbacks(updateProgressAction)
                if (player.state.value.isPlaying) {
                    val speed = player.features[PlaybackSpeed]?.value ?: 1f
                    val delay = if (speed > 0f) {
                        (progressBar.preferredUpdateDelay / speed).toLong().coerceIn(200L..1000L)
                    } else {
                        1000
                    }
                    root.postDelayed(updateProgressAction, delay)
                }
            }
        }
    }

    override fun restartPlayer() {
        playbackService?.restartPlayer()
    }

    override fun toggleAudioCompressor() {
        // The audio compressor was an ExoPlayer-only effect and is not part of mediamp's common API.
    }

    override fun setSubtitlesButton() {
        with(binding.playerControls) {
            if (hasSubtitles && requireContext().prefs().getBoolean(C.PLAYER_SUBTITLES, false)) {
                subtitles.visibility = View.VISIBLE
                if (subtitlesSelected) {
                    subtitles.setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_on)
                    subtitles.setOnClickListener {
                        showController(force = true)
                        toggleSubtitles(false)
                        requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, false) }
                    }
                } else {
                    subtitles.setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_off)
                    subtitles.setOnClickListener {
                        showController(force = true)
                        toggleSubtitles(true)
                        requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, true) }
                    }
                }
            } else {
                subtitles.visibility = View.GONE
            }
            (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setSubtitles(if (hasSubtitles) subtitlesSelected else null)
        }
    }

    override fun toggleSubtitles(enabled: Boolean) {
        playbackService?.toggleSubtitles(enabled)
    }

    override fun getUnavailableQualities(): List<VideoQuality> = emptyList()

    override fun showPlaylistTags(mediaPlaylist: Boolean) {
        // HLS playlist manifests are not exposed by mediamp's common API.
    }

    override fun changeQuality(selectedQuality: VideoQuality?) {
        playbackService?.changeQuality(selectedQuality)
    }

    override fun startAudioOnly() {
        if (playbackService != null) {
            playbackService?.startAudioOnly()
            playbackService?.setSleepTimer((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
            playbackService?.setStopServiceTimer(true)
        }
        cancelJobs()
        playbackService?.serviceListener = null
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
        playbackService = null
    }

    override fun close(deleteStates: Boolean) {
        playbackService?.player?.pause()
        playbackService?.player?.stopPlayback()
        if (deleteStates) {
            viewModel.deletePlaybackStates()
        }
        cancelJobs()
        playbackService?.serviceListener = null
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
        playbackService?.stopSelf()
        playbackService = null
    }

    override fun retry(item: String) {
        playbackService?.retry(item)
    }

    override fun onStop() {
        super.onStop()
        if (playbackService != null) {
            val isInPIPMode = requireActivity().isInPictureInPictureMode
            playbackService?.stop(isInPIPMode)
            playbackService?.setSleepTimer((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
            playbackService?.setStopServiceTimer(true)
        }
        binding.playerControls.root.removeCallbacks(updateProgressAction)
        cancelJobs()
        playbackService?.serviceListener = null
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            if (playbackService?.type == BasePlaybackService.STREAM) {
                restartPlayer()
            } else {
                playbackService?.retry("refreshVideo")
            }
        }
    }

    override fun onNetworkLost() {
        if (playbackService?.type != BasePlaybackService.STREAM && isResumed) {
            playbackService?.player?.stopPlayback()
        }
    }
}
