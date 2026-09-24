package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.PlaybackState
import com.github.andreyasadchy.xtra.model.VideoQuality
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Platform-agnostic playback session state and its serialization to/from [PlaybackState].
 * The Android service base class implements this by delegation, so the fields and the
 * restore/save logic live in shared code.
 */
interface PlaybackSession {
    var type: String?
    var streamId: String?
    var videoId: String?
    var clipId: String?
    var offlineVideoId: Int?
    var channelId: String?
    var channelLogin: String?
    var channelName: String?
    var channelImage: String?
    var gameId: String?
    var gameSlug: String?
    var gameName: String?
    var title: String?
    var thumbnail: String?
    var createdAt: String?
    var viewerCount: Int?
    var durationSeconds: Int?
    var videoType: String?
    var videoOffsetSeconds: Int?
    var videoCreatedAt: String?
    var videoAnimatedPreviewURL: String?
    var savedPosition: Long?
    var paused: Boolean
    var qualities: List<VideoQuality>?
    var quality: VideoQuality?
    var previousQuality: VideoQuality?
    var restoreQuality: Boolean
    var playlistUrl: String?
    var restorePlaylist: Boolean
    var skipAccessToken: Boolean
    var chatUrl: String?
    var started: Boolean
    var loaded: Boolean

    fun restore(state: PlaybackState, json: Json)

    fun toPlaybackState(position: Long?, paused: Boolean, json: Json): PlaybackState
}

class PlaybackSessionImpl : PlaybackSession {
    override var type: String? = null
    override var streamId: String? = null
    override var videoId: String? = null
    override var clipId: String? = null
    override var offlineVideoId: Int? = null
    override var channelId: String? = null
    override var channelLogin: String? = null
    override var channelName: String? = null
    override var channelImage: String? = null
    override var gameId: String? = null
    override var gameSlug: String? = null
    override var gameName: String? = null
    override var title: String? = null
    override var thumbnail: String? = null
    override var createdAt: String? = null
    override var viewerCount: Int? = null
    override var durationSeconds: Int? = null
    override var videoType: String? = null
    override var videoOffsetSeconds: Int? = null
    override var videoCreatedAt: String? = null
    override var videoAnimatedPreviewURL: String? = null
    override var savedPosition: Long? = null
    override var paused = false
    override var qualities: List<VideoQuality>? = null
    override var quality: VideoQuality? = null
    override var previousQuality: VideoQuality? = null
    override var restoreQuality = false
    override var playlistUrl: String? = null
    override var restorePlaylist = false
    override var skipAccessToken = false
    override var chatUrl: String? = null
    override var started = false
    override var loaded = false

    override fun restore(state: PlaybackState, json: Json) {
        type = state.type
        streamId = state.streamId
        videoId = state.videoId
        clipId = state.clipId
        offlineVideoId = state.offlineVideoId
        channelId = state.channelId
        channelLogin = state.channelLogin
        channelName = state.channelName
        channelImage = state.channelImage
        gameId = state.gameId
        gameSlug = state.gameSlug
        gameName = state.gameName
        title = state.title
        thumbnail = state.thumbnail
        createdAt = state.createdAt
        viewerCount = state.viewerCount
        durationSeconds = state.durationSeconds
        videoType = state.videoType
        videoOffsetSeconds = state.videoOffsetSeconds
        videoCreatedAt = state.videoCreatedAt
        videoAnimatedPreviewURL = state.videoAnimatedPreviewURL
        savedPosition = state.position
        paused = state.paused
        qualities = state.qualities?.let { qualities ->
            json.decodeFromString<JsonArray>(qualities).map {
                json.decodeFromJsonElement<VideoQuality>(it)
            }
        }
        quality = state.quality?.let { json.decodeFromString(it) }
        previousQuality = state.previousQuality?.let { json.decodeFromString(it) }
        restoreQuality = state.restoreQuality
        playlistUrl = state.playlistUrl
        restorePlaylist = state.restorePlaylist
        skipAccessToken = state.skipAccessToken
    }

    override fun toPlaybackState(position: Long?, paused: Boolean, json: Json): PlaybackState {
        return PlaybackState(
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
                        add(json.encodeToJsonElement(it))
                    }
                }.toString()
            },
            quality = quality?.let { json.encodeToString(it) },
            previousQuality = previousQuality?.let { json.encodeToString(it) },
            restoreQuality = restoreQuality,
            playlistUrl = playlistUrl,
            restorePlaylist = restorePlaylist,
            skipAccessToken = skipAccessToken,
        )
    }
}
