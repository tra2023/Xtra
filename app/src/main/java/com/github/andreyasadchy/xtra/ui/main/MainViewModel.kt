package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.PlaybackState
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.repository.getString
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Timer
import kotlin.math.max
import kotlin.time.Instant

class MainViewModel(
    private val applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val playerRepository: PlayerRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val authRepository: AuthRepository,
    private val xtraHttpClient: XtraHttpClient,
    private val json: Json,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()
    var loadingIntegrityToken = false

    val checkNetworkStatus = MutableStateFlow(false)
    val checkCellularStatus = MutableStateFlow(false)
    val isNetworkAvailable = MutableStateFlow<Boolean?>(null)

    var isPlayerOpened = false
    val playbackStates = MutableSharedFlow<List<PlaybackState>>()
    var loadingPlaybackStates = false
    val startDownloadService = MutableSharedFlow<Pair<Int, Boolean>>()

    var sleepTimer: Timer? = null
    var sleepTimerEndTime = 0L

    val video = MutableStateFlow<Pair<Video?, Long?>?>(null)
    val clip = MutableStateFlow<Clip?>(null)
    val user = MutableStateFlow<User?>(null)
    val game = MutableStateFlow<Pair<Game?, String?>?>(null)
    val tag = MutableStateFlow<Tag?>(null)

    val updateUrl = MutableSharedFlow<String?>()
    var updateSize: Long? = null
    var updateJob: Job? = null
    val updateProgress = MutableSharedFlow<Int>()
    val closeUpdateDialog = MutableSharedFlow<Boolean>()

    fun savePlaybackState(item: PlaybackState) {
        viewModelScope.launch {
            playerRepository.savePlaybackStates(listOf(item))
        }
    }

    fun getPlaybackStates() {
        if (!loadingPlaybackStates) {
            loadingPlaybackStates = true
            viewModelScope.launch {
                playbackStates.emit(playerRepository.getPlaybackStates())
            }.invokeOnCompletion {
                loadingPlaybackStates = false
            }
        }
    }

    suspend fun getWaitingDownloads(): List<OfflineVideo> {
        return offlineVideosRepository.getWaitingDownloads()
    }

    fun loadVideo(videoId: String?, offset: Long?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (video.value == null) {
            viewModelScope.launch {
                val item = try {
                    val response = graphQLRepository.loadQueryVideo(gqlHeaders, videoId)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
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
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getVideos(
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
                video.value = item to offset
            }
        }
    }

    fun saveVideoPosition(id: Long, position: Long) {
        viewModelScope.launch {
            playerRepository.saveVideoPosition(VideoPosition(id, position))
        }
    }

    suspend fun savePosition(id: Long, position: Long) {
        playerRepository.saveVideoPosition(VideoPosition(id, position))
    }

    fun saveOfflineVideoPosition(id: Int, position: Long) {
        viewModelScope.launch {
            offlineVideosRepository.updatePosition(id, position)
        }
    }

    fun loadClip(clipId: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (clip.value == null) {
            viewModelScope.launch {
                clip.value = try {
                    val response = graphQLRepository.loadQueryClip(gqlHeaders, clipId!!)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
                        }
                    }
                    response.data!!.clip?.let {
                        Clip(
                            id = clipId,
                            channelId = it.broadcaster?.id,
                            channelLogin = it.broadcaster?.login,
                            channelName = it.broadcaster?.displayName,
                            channelImageURL = it.broadcaster?.profileImageURL,
                            gameId = it.game?.id,
                            gameSlug = it.game?.slug,
                            gameName = it.game?.displayName,
                            title = it.title,
                            thumbnailURL = it.thumbnailURL,
                            createdAt = it.createdAt?.toString(),
                            durationSeconds = it.durationSeconds,
                            videoId = it.video?.id,
                            videoOffsetSeconds = run {
                                val videoOffsetSeconds = it.videoOffsetSeconds
                                val durationSeconds = it.durationSeconds
                                if (videoOffsetSeconds != null && durationSeconds != null) {
                                    max(videoOffsetSeconds - durationSeconds, 0)
                                } else {
                                    videoOffsetSeconds
                                }
                            },
                            videoCreatedAt = it.video?.createdAt?.toString(),
                            videoAnimatedPreviewURL = it.video?.animatedPreviewURL,
                        )
                    }
                } catch (e: Exception) {
                    try {
                        val user = try {
                            graphQLRepository.loadClipData(gqlHeaders, clipId).data?.clip
                        } catch (e: Exception) {
                            null
                        }
                        val clip = graphQLRepository.loadClipVideo(gqlHeaders, clipId).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("refresh")
                                    return@launch
                                }
                            }
                        }.data?.clip
                        Clip(
                            id = clipId,
                            channelId = user?.broadcaster?.id,
                            channelLogin = user?.broadcaster?.login,
                            channelName = user?.broadcaster?.displayName,
                            channelImageURL = user?.broadcaster?.profileImageURL,
                            durationSeconds = clip?.durationSeconds,
                            videoId = clip?.video?.id,
                            videoOffsetSeconds = (clip?.videoOffsetSeconds ?: user?.videoOffsetSeconds).let {
                                val clipDurationSeconds = clip?.durationSeconds
                                if (it != null && clipDurationSeconds != null) {
                                    max(it - clipDurationSeconds, 0)
                                } else {
                                    it
                                }
                            },
                        )
                    } catch (e: Exception) {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                            try {
                                helixRepository.getClips(
                                    headers = helixHeaders,
                                    ids = clipId?.let { listOf(it) }
                                ).data.firstOrNull()?.let {
                                    Clip(
                                        id = it.id,
                                        channelId = it.channelId,
                                        channelName = it.channelName,
                                        gameId = it.gameId,
                                        title = it.title,
                                        thumbnailURL = it.thumbnailURL,
                                        createdAt = it.createdAt,
                                        viewCount = it.viewCount,
                                        durationSeconds = it.duration?.toInt(),
                                        videoId = it.videoId,
                                        videoOffsetSeconds = run {
                                            val vodOffset = it.vodOffset
                                            val vodDuration = it.duration
                                            if (vodOffset != null && vodDuration != null) {
                                                max(vodOffset - vodDuration.toInt(), 0)
                                            } else {
                                                vodOffset
                                            }
                                        },
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }
                }
            }
        }
    }

    fun loadUser(login: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (user.value == null) {
            viewModelScope.launch {
                user.value = try {
                    val response = graphQLRepository.loadQueryUser(gqlHeaders, login = login)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
                        }
                    }
                    response.data!!.user?.let {
                        User(
                            id = it.id,
                            login = it.login,
                            name = it.displayName,
                            profileImageURL = it.profileImageURL,
                        )
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getUsers(
                                headers = helixHeaders,
                                logins = login?.let { listOf(it) }
                            ).data.firstOrNull()?.let {
                                User(
                                    id = it.id,
                                    login = it.login,
                                    name = it.displayName,
                                    profileImageURL = it.profileImageURL,
                                    type = it.type,
                                    broadcasterType = it.broadcasterType,
                                    createdAt = it.createdAt,
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    fun loadGame(gameSlug: String? = null, gameName: String? = null, tag: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (game.value == null) {
            viewModelScope.launch {
                game.value = try {
                    val response = graphQLRepository.loadQueryGame(
                        headers = gqlHeaders,
                        slug = gameSlug,
                        name = gameName.takeIf { gameSlug.isNullOrBlank() },
                    )
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
                        }
                    }
                    response.data!!.game?.let {
                        Game(
                            id = it.id,
                            slug = it.slug,
                            name = it.displayName,
                            boxArtURL = it.boxArtURL,
                        )
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && !gameName.isNullOrBlank()) {
                        try {
                            helixRepository.getGames(
                                headers = helixHeaders,
                                names = listOf(gameName)
                            ).data.firstOrNull()?.let {
                                Game(
                                    id = it.id,
                                    name = it.name,
                                    boxArtURL = it.boxArtURL,
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                } to tag
            }
        }
    }

    fun loadTag(tagId: String, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (tag.value == null) {
            viewModelScope.launch {
                tag.value = try {
                    val response = graphQLRepository.loadQueryTag(gqlHeaders, tagId)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
                        }
                    }
                    response.data!!.contentTag?.let {
                        Tag(
                            id = tagId,
                            name = it.localizedName,
                        )
                    }
                } catch (e: Exception) {
                    try {
                        val response = graphQLRepository.loadTag(gqlHeaders, tagId)
                        if (enableIntegrity) {
                            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                integrity.emit("refresh")
                                return@launch
                            }
                        }
                        response.data!!.contentTag.let {
                            Tag(
                                id = tagId,
                                name = it.localizedName,
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    fun downloadStream(filesDir: String, id: String?, title: String?, createdAt: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?, downloadPath: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            if (!channelLogin.isNullOrBlank()) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    thumbnail.takeIf { !it.isNullOrBlank() }?.let { url ->
                        File(filesDir, "thumbnails").mkdir()
                        val path = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    channelImage.takeIf { !it.isNullOrBlank() }?.let { url ->
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val waitForWifi = if (wifiOnly) {
                    val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                } else false
                val videoId = offlineVideosRepository.save(
                    OfflineVideo(
                        name = title,
                        channelId = channelId,
                        channelLogin = channelLogin,
                        channelName = channelName,
                        channelLogo = downloadedLogo,
                        thumbnail = downloadedThumbnail,
                        gameId = gameId,
                        gameSlug = gameSlug,
                        gameName = gameName,
                        uploadDate = createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
                        downloadDate = System.currentTimeMillis(),
                        downloadPath = downloadPath,
                        status = if (waitForWifi) {
                            OfflineVideo.STATUS_WAITING_FOR_WIFI
                        } else {
                            OfflineVideo.STATUS_PENDING
                        },
                        quality = if (!quality.contains("Audio", true)) quality else "audio",
                        downloadChat = downloadChat,
                        downloadChatEmotes = downloadChatEmotes,
                        live = true
                    )
                ).toInt()
                if (!waitForWifi) {
                    startDownloadService.emit(Pair(videoId, true))
                }
            }
        }
    }

    fun downloadVideo(filesDir: String, id: String?, title: String?, createdAt: String?, type: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?, url: String, downloadPath: String, quality: String, from: Long, to: Long, downloadChat: Boolean, downloadChatEmotes: Boolean, playlistToFile: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                thumbnail.takeIf { !it.isNullOrBlank() }?.let { url ->
                    File(filesDir, "thumbnails").mkdir()
                    val path = filesDir + File.separator + "thumbnails" + File.separator + id
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                        } catch (e: Exception) {

                        }
                    }
                    path
                }
            }
            val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                channelImage.takeIf { !it.isNullOrBlank() }?.let { url ->
                    File(filesDir, "profile_pics").mkdir()
                    val path = filesDir + File.separator + "profile_pics" + File.separator + id
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                        } catch (e: Exception) {

                        }
                    }
                    path
                }
            }
            val waitForWifi = if (wifiOnly) {
                val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else false
            val videoId = offlineVideosRepository.save(
                OfflineVideo(
                    sourceUrl = url,
                    name = title,
                    channelId = channelId,
                    channelLogin = channelLogin,
                    channelName = channelName,
                    channelLogo = downloadedLogo,
                    thumbnail = downloadedThumbnail,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
                    uploadDate = createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
                    downloadDate = System.currentTimeMillis(),
                    downloadPath = downloadPath,
                    fromTime = from,
                    toTime = to,
                    status = if (waitForWifi) {
                        OfflineVideo.STATUS_WAITING_FOR_WIFI
                    } else {
                        OfflineVideo.STATUS_PENDING
                    },
                    type = type,
                    videoId = id,
                    quality = if (!quality.contains("Audio", true)) quality else "audio",
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes,
                    playlistToFile = playlistToFile
                )
            ).toInt()
            if (!waitForWifi) {
                startDownloadService.emit(Pair(videoId, false))
            }
        }
    }

    fun downloadClip(filesDir: String, clipId: String?, title: String?, createdAt: String?, durationSeconds: Int?, videoId: String?, videoOffsetSeconds: Int?, videoCreatedAt: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?, url: String, downloadPath: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            val downloadedThumbnail = clipId.takeIf { !it.isNullOrBlank() }?.let { id ->
                thumbnail.takeIf { !it.isNullOrBlank() }?.let { url ->
                    File(filesDir, "thumbnails").mkdir()
                    val path = filesDir + File.separator + "thumbnails" + File.separator + id
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                        } catch (e: Exception) {

                        }
                    }
                    path
                }
            }
            val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                channelImage.takeIf { !it.isNullOrBlank() }?.let { url ->
                    File(filesDir, "profile_pics").mkdir()
                    val path = filesDir + File.separator + "profile_pics" + File.separator + id
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                        } catch (e: Exception) {

                        }
                    }
                    path
                }
            }
            val waitForWifi = if (wifiOnly) {
                val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else false
            val videoId = offlineVideosRepository.save(
                OfflineVideo(
                    sourceUrl = url,
                    sourceStartPosition = videoOffsetSeconds?.toLong()?.times(1000L),
                    name = title,
                    channelId = channelId,
                    channelLogin = channelLogin,
                    channelName = channelName,
                    channelLogo = downloadedLogo,
                    thumbnail = downloadedThumbnail,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
                    duration = durationSeconds?.times(1000L),
                    uploadDate = createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
                    downloadDate = System.currentTimeMillis(),
                    downloadPath = downloadPath,
                    status = if (waitForWifi) {
                        OfflineVideo.STATUS_WAITING_FOR_WIFI
                    } else {
                        OfflineVideo.STATUS_PENDING
                    },
                    videoId = videoId,
                    videoCreatedAt = videoCreatedAt,
                    clipId = clipId,
                    quality = if (!quality.contains("Audio", true)) quality else "audio",
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes
                )
            ).toInt()
            if (!waitForWifi) {
                startDownloadService.emit(Pair(videoId, false))
            }
        }
    }

    fun validate(gqlHeaders: Map<String, String>, gqlWebClientId: String?, gqlWebToken: String?, helixHeaders: Map<String, String>, accountId: String?, accountLogin: String?, activity: Activity) {
        viewModelScope.launch {
            try {
                val helixToken = helixHeaders[C.HEADER_TOKEN]
                if (!helixToken.isNullOrBlank()) {
                    val response = authRepository.validate(helixToken)
                    if (response.clientId.isNotBlank() && response.clientId == helixHeaders[C.HEADER_CLIENT_ID]) {
                        if ((!response.userId.isNullOrBlank() && response.userId != accountId) || (!response.login.isNullOrBlank() && response.login != accountLogin)) {
                            activity.tokenPrefs().edit {
                                putString(C.USER_ID, response.userId?.takeIf { it.isNotBlank() } ?: accountId)
                                putString(C.USERNAME, response.login?.takeIf { it.isNotBlank() } ?: accountLogin)
                            }
                        }
                    } else {
                        throw IllegalStateException("401")
                    }
                }
                val gqlToken = gqlHeaders[C.HEADER_TOKEN]
                if (!gqlToken.isNullOrBlank()) {
                    val response = authRepository.validate(gqlToken)
                    if (response.clientId.isNotBlank() && (response.clientId == gqlHeaders[C.HEADER_CLIENT_ID] || response.clientId == gqlWebClientId)) {
                        if ((!response.userId.isNullOrBlank() && response.userId != accountId) || (!response.login.isNullOrBlank() && response.login != accountLogin)) {
                            activity.tokenPrefs().edit {
                                putString(C.USER_ID, response.userId?.takeIf { it.isNotBlank() } ?: accountId)
                                putString(C.USERNAME, response.login?.takeIf { it.isNotBlank() } ?: accountLogin)
                            }
                        }
                    } else {
                        throw IllegalStateException("401")
                    }
                }
                if (!gqlWebToken.isNullOrBlank() && gqlWebToken != gqlToken) {
                    val response = authRepository.validate(gqlWebToken)
                    if (response.clientId.isNotBlank() && response.clientId == gqlWebClientId) {
                        if ((!response.userId.isNullOrBlank() && response.userId != accountId) || (!response.login.isNullOrBlank() && response.login != accountLogin)) {
                            activity.tokenPrefs().edit {
                                putString(C.USER_ID, response.userId?.takeIf { it.isNotBlank() } ?: accountId)
                                putString(C.USERNAME, response.login?.takeIf { it.isNotBlank() } ?: accountLogin)
                            }
                        }
                    } else {
                        throw IllegalStateException("401")
                    }
                }
            } catch (e: Exception) {
                if (e is IllegalStateException && e.message == "401") {
                    Toast.makeText(activity, R.string.token_expired, Toast.LENGTH_LONG).show()
                    (activity as? MainActivity)?.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                }
            }
        }
        TwitchApiHelper.checkedValidation = true
    }

    fun checkUpdates(url: String, lastChecked: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            updateUrl.emit(
                try {
                    val response = json.decodeFromString<JsonObject>(xtraHttpClient.getString(url))
                    response["assets"]?.jsonArray?.find {
                        it.jsonObject.getValue("content_type").jsonPrimitive.contentOrNull == "application/vnd.android.package-archive"
                    }?.jsonObject?.let { obj ->
                        obj.getValue("updated_at").jsonPrimitive.contentOrNull?.let {
                            Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }
                        }?.let {
                            if (it > lastChecked) {
                                updateSize = obj["size"]?.jsonPrimitive?.longOrNull
                                obj.getValue("browser_download_url").jsonPrimitive.contentOrNull
                            } else null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            )
        }
        TwitchApiHelper.checkedUpdates = true
    }

    fun downloadUpdate(url: String) {
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = xtraHttpClient.download(url) { bytesRead, _ -> runBlocking { updateProgress.emit(bytesRead.toInt()) } }
                if (response.isNotEmpty()) {
                    val packageInstaller = applicationContext.packageManager.packageInstaller
                    val sessionId = packageInstaller.createSession(
                        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    )
                    val session = packageInstaller.openSession(sessionId)
                    session.openWrite("package", 0, response.size.toLong()).use {
                        it.write(response)
                    }
                    session.commit(
                        PendingIntent.getActivity(
                            applicationContext,
                            0,
                            Intent(applicationContext, MainActivity::class.java).apply {
                                setAction(MainActivity.INTENT_INSTALL_UPDATE)
                            },
                            PendingIntent.FLAG_MUTABLE
                        ).intentSender
                    )
                    session.close()
                }
            } catch (e: Exception) {

            }
            closeUpdateDialog.emit(true)
        }
    }

    fun deleteOldImages() {
        viewModelScope.launch(Dispatchers.IO) {
            localChannelFollowsRepository.deleteOldImages()
        }
    }

    companion object {
        val MainViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                MainViewModel(application.applicationContext, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.playerRepository, xtraModule.offlineVideosRepository, xtraModule.localChannelFollowsRepository, xtraModule.authRepository, xtraModule.xtraHttpClient, xtraModule.json)
            }
        }
    }
}