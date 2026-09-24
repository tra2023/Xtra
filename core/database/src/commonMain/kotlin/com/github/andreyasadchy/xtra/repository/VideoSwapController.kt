package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Platform-agnostic state machine for stream ad handling: video swap and "hide ads".
 * The [Host] supplies the player actions and the persisted quality setting.
 */
class VideoSwapController(
    private val playerRepository: PlayerRepository,
    private val scope: CoroutineScope,
    private val host: Host,
) {
    interface Host {
        fun restartPlayer()
        fun setVideoHidden(hidden: Boolean)
        fun onVideoSwapActive()
        fun onWaitingAds()
        fun savedQuality(): String?
        fun setSavedQuality(value: String?)
    }

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

    val isHidden: Boolean get() = hidden

    val isVideoSwapEnabled: Boolean get() = useVideoSwap

    suspend fun init(enable: Boolean) {
        useVideoSwap = enable
        if (enable) {
            videoSwapList = playerRepository.getVideoSwapItems().filter {
                it.enabled && !it.platform.isNullOrBlank() && !it.playerType.isNullOrBlank()
            }.sortedBy { it.position }
        }
    }

    fun shouldProcess(hideAds: Boolean): Boolean = (useVideoSwap && !stopVideoSwap) || hideAds

    fun currentSwapItemOrNull(): VideoSwap? =
        if (videoSwapActive) videoSwapList?.getOrNull(currentVideoSwapItem) else null

    fun onStreamLoaded() {
        videoSwapLoading = false
    }

    fun onAdsChanged(isAd: Boolean, hideAds: Boolean, playbackUrl: String?, isAudioOnly: Boolean) {
        val useVideoSwap = useVideoSwap && !stopVideoSwap
        if (!useVideoSwap && !hideAds) {
            return
        }
        val oldValue = playingAds
        playingAds = isAd
        if (isAd) {
            if (videoSwapActive) {
                if (!videoSwapLoading) {
                    videoSwapLoading = true
                    currentVideoSwapItem += 1
                    if (videoSwapList?.getOrNull(currentVideoSwapItem) != null && !playbackUrl.isNullOrBlank()) {
                        startAdPoll(playbackUrl)
                    } else {
                        videoSwapActive = false
                        stopVideoSwap = true
                        checkPlaylistJob?.cancel()
                        checkPlaylistJob = null
                    }
                    restoreQuality()
                    host.restartPlayer()
                }
            } else if (!oldValue) {
                when {
                    useVideoSwap && !playbackUrl.isNullOrBlank() -> {
                        if (!videoSwapLoading) {
                            videoSwapLoading = true
                            videoSwapActive = true
                            host.onVideoSwapActive()
                            startAdPoll(playbackUrl)
                            videoSwapPreviousQuality = host.savedQuality()
                            host.restartPlayer()
                        }
                    }
                    hideAds && !hidden -> {
                        hidden = true
                        host.setVideoHidden(true)
                        host.onWaitingAds()
                    }
                }
            }
        } else if (hidden) {
            hidden = false
            host.setVideoHidden(false)
        }
    }

    private fun startAdPoll(playbackUrl: String) {
        checkPlaylistJob?.cancel()
        checkPlaylistJob = scope.launch {
            for (i in 0 until 60) {
                delay(2.seconds)
                if (!playerRepository.checkForAds(playbackUrl)) {
                    break
                }
            }
            videoSwapLoading = true
            videoSwapActive = false
            restoreQuality()
            host.restartPlayer()
            checkPlaylistJob = null
        }
    }

    private fun restoreQuality() {
        val previousQuality = videoSwapPreviousQuality
        if (host.savedQuality() != previousQuality) {
            host.setSavedQuality(previousQuality)
        }
    }
}
