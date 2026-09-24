package com.github.andreyasadchy.xtra.ui.player

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.LifecycleService
import com.github.andreyasadchy.xtra.XtraModule
import com.github.andreyasadchy.xtra.model.PlaybackState
import com.github.andreyasadchy.xtra.model.PlaybackType
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.VideoQualityUtils
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

abstract class BasePlaybackService : LifecycleService() {

    lateinit var xtraModule: XtraModule

    val integrity = MutableSharedFlow<String?>()

    var type: String? = null
    var streamId: String? = null
    var videoId: String? = null
    var clipId: String? = null
    var offlineVideoId: Int? = null
    var channelId: String? = null
    var channelLogin: String? = null
    var channelName: String? = null
    var channelImage: String? = null
    var gameId: String? = null
    var gameSlug: String? = null
    var gameName: String? = null
    var title: String? = null
    var thumbnail: String? = null
    var createdAt: String? = null
    var viewerCount: Int? = null
    var durationSeconds: Int? = null
    var videoType: String? = null
    var videoOffsetSeconds: Int? = null
    var videoCreatedAt: String? = null
    var videoAnimatedPreviewURL: String? = null
    var savedPosition: Long? = null
    var paused = false
    var qualities: List<VideoQuality>? = null
    var quality: VideoQuality? = null
    var previousQuality: VideoQuality? = null
    var restoreQuality = false
    var playlistUrl: String? = null
    var restorePlaylist = false
    var skipAccessToken = false

    var chatUrl: String? = null
    var started = false
    var loaded = false

    protected suspend fun restorePlaybackState() {
        val savedState = xtraModule.playerRepository.getPlaybackStates().firstOrNull()
        xtraModule.playerRepository.deletePlaybackStates()
        if (savedState != null) {
            type = savedState.type
            streamId = savedState.streamId
            videoId = savedState.videoId
            clipId = savedState.clipId
            offlineVideoId = savedState.offlineVideoId
            channelId = savedState.channelId
            channelLogin = savedState.channelLogin
            channelName = savedState.channelName
            channelImage = savedState.channelImage
            gameId = savedState.gameId
            gameSlug = savedState.gameSlug
            gameName = savedState.gameName
            title = savedState.title
            thumbnail = savedState.thumbnail
            createdAt = savedState.createdAt
            viewerCount = savedState.viewerCount
            durationSeconds = savedState.durationSeconds
            videoType = savedState.videoType
            videoOffsetSeconds = savedState.videoOffsetSeconds
            videoCreatedAt = savedState.videoCreatedAt
            videoAnimatedPreviewURL = savedState.videoAnimatedPreviewURL
            savedPosition = savedState.position
            paused = savedState.paused
            qualities = savedState.qualities?.let { qualities ->
                xtraModule.json.decodeFromString<JsonArray>(qualities).map {
                    xtraModule.json.decodeFromJsonElement<VideoQuality>(it)
                }
            }
            quality = savedState.quality?.let { xtraModule.json.decodeFromString(it) }
            previousQuality = savedState.previousQuality?.let { xtraModule.json.decodeFromString(it) }
            restoreQuality = savedState.restoreQuality
            playlistUrl = savedState.playlistUrl
            restorePlaylist = savedState.restorePlaylist
            skipAccessToken = savedState.skipAccessToken
        }
    }

    protected suspend fun savePlaybackState(position: Long?, paused: Boolean) {
        val item = PlaybackState(
            type = type,
            streamId = streamId,
            videoId = videoId,
            clipId = clipId,
            offlineVideoId = offlineVideoId,
            channelId = channelId,
            channelLogin = channelLogin,
            channelName = channelName,
            channelImage = channelImage,
            gameId = gameId,
            gameSlug = gameSlug,
            gameName = gameName,
            title = title,
            thumbnail = thumbnail,
            createdAt = createdAt,
            viewerCount = viewerCount,
            durationSeconds = durationSeconds,
            videoType = videoType,
            videoOffsetSeconds = videoOffsetSeconds,
            videoCreatedAt = videoCreatedAt,
            videoAnimatedPreviewURL = videoAnimatedPreviewURL,
            position = position,
            paused = paused,
            qualities = qualities?.let { qualities ->
                buildJsonArray {
                    qualities.forEach {
                        add(xtraModule.json.encodeToJsonElement(it))
                    }
                }.toString()
            },
            quality = quality?.let { xtraModule.json.encodeToString(it) },
            previousQuality = previousQuality?.let { xtraModule.json.encodeToString(it) },
            restoreQuality = restoreQuality,
            playlistUrl = playlistUrl,
            restorePlaylist = restorePlaylist,
            skipAccessToken = skipAccessToken,
        )
        xtraModule.playerRepository.savePlaybackStates(listOf(item))
    }

    protected fun setDefaultQuality() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val cellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        quality = VideoQualityUtils.selectDefaultQuality(
            qualities = qualities,
            cellular = cellular,
            defaultCellularQuality = prefs().getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved"),
            defaultQuality = prefs().getString(C.PLAYER_DEFAULT_QUALITY, "saved"),
            savedQuality = prefs().getString(C.PLAYER_QUALITY, "720p60"),
        )
    }

    companion object {
        const val STREAM = PlaybackType.STREAM
        const val VIDEO = PlaybackType.VIDEO
        const val CLIP = PlaybackType.CLIP
        const val OFFLINE_VIDEO = PlaybackType.OFFLINE_VIDEO
    }
}