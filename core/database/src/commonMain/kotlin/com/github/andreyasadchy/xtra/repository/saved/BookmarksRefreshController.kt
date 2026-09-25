package com.github.andreyasadchy.xtra.repository.saved

import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchImageUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Platform-agnostic bookmark refresh logic, ported from
 * `app/.../ui/saved/bookmarks/BookmarksViewModel.kt`
 * (`updateUsers`, `updateVideo`, `updateVideos`).
 *
 * Same pattern as the browse controllers: repositories injected, caller
 * supplies the [CoroutineScope] (`viewModelScope` on Android). Thumbnail file
 * IO stays behind [thumbnailPath]/[writeThumbnail] (the app's `filesDir`
 * layout), and integrity failures report through [onIntegrityFailed] instead
 * of the Android `ViewModel`'s shared flow.
 */
class BookmarksRefreshController(
    private val scope: CoroutineScope,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val thumbnailPath: (filesDir: String, id: String) -> String,
    private val writeThumbnail: suspend (path: String, url: String) -> Unit,
    private val onIntegrityFailed: (String) -> Unit,
) {
    private var updatedUsers = false
    private var updatedVideos = false

    fun updateUsers(gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (!updatedUsers) {
            scope.launch {
                val bookmarks = bookmarksRepository.getAll()
                val ignored = bookmarksRepository.getIgnoredUsers()
                bookmarks.mapNotNull { bookmark ->
                    bookmark.userId?.takeIf { ignored.find { it.userId == bookmark.userId } == null }
                }.chunked(100).forEach { ids ->
                    try {
                        val response = graphQLRepository.loadQueryUsersType(gqlHeaders, ids)
                        if (enableIntegrity) {
                            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                onIntegrityFailed("users")
                                return@launch
                            }
                        }
                        response.data!!.users?.mapNotNull {
                            if (it != null) {
                                User(
                                    id = it.id,
                                    broadcasterType = when {
                                        it.roles?.isPartner == true -> "partner"
                                        it.roles?.isAffiliate == true -> "affiliate"
                                        else -> null
                                    },
                                    type = when {
                                        it.roles?.isStaff == true -> "staff"
                                        else -> null
                                    },
                                )
                            } else null
                        }
                    } catch (e: Exception) {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                            try {
                                helixRepository.getUsers(
                                    headers = helixHeaders,
                                    ids = ids,
                                ).data.map {
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
                    }?.forEach { user ->
                        user.id?.let { id ->
                            bookmarks.filter { it.userId == id }
                        }?.forEach { bookmark ->
                            if (user.type != bookmark.userType || user.broadcasterType != bookmark.userBroadcasterType) {
                                bookmarksRepository.update(bookmark.apply {
                                    userType = user.type
                                    userBroadcasterType = user.broadcasterType
                                })
                            }
                        }
                    }
                }
                updatedUsers = true
            }
        }
    }

    fun updateVideo(filesDir: String, videoId: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        scope.launch {
            if (!videoId.isNullOrBlank()) {
                val video = try {
                    val response = graphQLRepository.loadQueryVideo(gqlHeaders, videoId)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            onIntegrityFailed("video")
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
                                ids = listOf(videoId),
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
                                    durationSeconds = it.duration?.let { duration -> TwitchImageUrls.getDuration(duration) },
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
                val bookmark = bookmarksRepository.getByVideoId(videoId)
                if (video != null && bookmark != null) {
                    val downloadedThumbnail = video.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                        video.thumbnail.takeIf { !it.isNullOrBlank() }?.let { url ->
                            downloadThumbnail(filesDir, id, url)
                        }
                    }
                    bookmarksRepository.update(
                        Bookmark(
                            videoId = bookmark.videoId,
                            userId = video.channelId ?: bookmark.userId,
                            userLogin = video.channelLogin ?: bookmark.userLogin,
                            userName = video.channelName ?: bookmark.userName,
                            userType = bookmark.userType,
                            userBroadcasterType = bookmark.userBroadcasterType,
                            userLogo = bookmark.userLogo,
                            gameId = video.gameId ?: bookmark.gameId,
                            gameSlug = video.gameSlug ?: bookmark.gameSlug,
                            gameName = video.gameName ?: bookmark.gameName,
                            title = video.title ?: bookmark.title,
                            createdAt = video.createdAt ?: bookmark.createdAt,
                            thumbnail = downloadedThumbnail,
                            type = video.type ?: bookmark.type,
                            duration = video.durationSeconds?.toString() ?: bookmark.duration,
                            animatedPreviewURL = video.animatedPreviewURL ?: bookmark.animatedPreviewURL
                        )
                    )
                }
            }
        }
    }

    fun updateVideos(filesDir: String, helixHeaders: Map<String, String>) {
        if (!updatedVideos) {
            scope.launch {
                val bookmarks = bookmarksRepository.getAll()
                bookmarks.mapNotNull { it.videoId }.chunked(100).forEach { ids ->
                    try {
                        helixRepository.getVideos(
                            headers = helixHeaders,
                            ids = ids,
                        ).data.map {
                            Video(
                                id = it.id,
                                channelId = it.channelId,
                                channelLogin = it.channelLogin,
                                channelName = it.channelName,
                                title = it.title,
                                thumbnailURL = it.thumbnailURL,
                                createdAt = it.createdAt,
                                viewCount = it.viewCount,
                                durationSeconds = it.duration?.let { duration -> TwitchImageUrls.getDuration(duration) },
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }?.forEach { video ->
                        video.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                            bookmarks.find { it.videoId == id }
                        }?.let { bookmark ->
                            if (bookmark.userId != video.channelId ||
                                bookmark.userLogin != video.channelLogin ||
                                bookmark.userName != video.channelName ||
                                bookmark.title != video.title ||
                                bookmark.createdAt != video.createdAt ||
                                bookmark.type != video.type ||
                                bookmark.duration != video.durationSeconds?.toString()
                            ) {
                                val downloadedThumbnail = video.thumbnail.takeIf { !it.isNullOrBlank() }?.let { url ->
                                    video.id?.takeIf { !it.isNullOrBlank() }?.let { id ->
                                        downloadThumbnail(filesDir, id, url)
                                    }
                                }
                                bookmarksRepository.update(
                                    Bookmark(
                                        videoId = bookmark.videoId,
                                        userId = video.channelId ?: bookmark.userId,
                                        userLogin = video.channelLogin ?: bookmark.userLogin,
                                        userName = video.channelName ?: bookmark.userName,
                                        userType = bookmark.userType,
                                        userBroadcasterType = bookmark.userBroadcasterType,
                                        userLogo = bookmark.userLogo,
                                        gameId = bookmark.gameId,
                                        gameSlug = bookmark.gameSlug,
                                        gameName = bookmark.gameName,
                                        title = video.title ?: bookmark.title,
                                        createdAt = video.createdAt ?: bookmark.createdAt,
                                        thumbnail = downloadedThumbnail,
                                        type = video.type ?: bookmark.type,
                                        duration = video.durationSeconds?.toString() ?: bookmark.duration,
                                        animatedPreviewURL = video.animatedPreviewURL ?: bookmark.animatedPreviewURL
                                    )
                                )
                            }
                        }
                    }
                }
                updatedVideos = true
            }
        }
    }

    private fun downloadThumbnail(filesDir: String, id: String, url: String): String {
        val path = thumbnailPath(filesDir, id)
        scope.launch(Dispatchers.IO) {
            try {
                writeThumbnail(path, url)
            } catch (e: Exception) {
            }
        }
        return path
    }
}
