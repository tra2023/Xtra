package com.github.andreyasadchy.xtra.repository.saved

import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Toggles a video bookmark, downloading the thumbnail and channel logo into [filesDir] first.
 *
 * This was copy-pasted verbatim into five view models (`ChannelVideosViewModel`, `GameVideosViewModel`,
 * `FollowedVideosViewModel`, `VideoSearchViewModel` and `PlayerViewModel`); the only difference between
 * them was whether the video arrived as a [Video] or as loose fields, which the two `save` overloads
 * cover.
 *
 * Bookmarking an already-bookmarked video removes it (and [BookmarksRepository] cleans up the cached
 * images when nothing else references them).
 */
class VideoBookmarker(
    private val scope: CoroutineScope,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val xtraHttpClient: XtraHttpClient,
) {

    /** Bookmark toggle for a paged [Video] row. */
    suspend fun save(filesDir: String, video: Video, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        val existing = video.id?.let { bookmarksRepository.getByVideoId(it) }
        if (existing != null) {
            bookmarksRepository.delete(existing)
            return
        }
        val bookmark = bookmarkOf(
            filesDir = filesDir,
            videoId = video.id,
            title = video.title,
            uploadDate = video.createdAt,
            duration = video.durationSeconds?.toString(),
            type = video.type,
            animatedPreviewUrl = video.animatedPreviewURL,
            channelId = video.channelId,
            channelLogin = video.channelLogin,
            channelName = video.channelName,
            channelImage = video.channelImage,
            thumbnail = video.thumbnail,
            gameId = video.gameId,
            gameSlug = video.gameSlug,
            gameName = video.gameName,
            gqlHeaders = gqlHeaders,
            helixHeaders = helixHeaders,
        )
        bookmarksRepository.save(bookmark)
    }

    /** Fire-and-forget [save] for hosts that only have a scope, not a suspend context. */
    fun toggle(filesDir: String, video: Video, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        scope.launch {
            save(filesDir, video, gqlHeaders, helixHeaders)
        }
    }

    /** Fire-and-forget [save] for the player, which only knows the individual fields. */
    fun toggle(
        filesDir: String,
        gqlHeaders: Map<String, String>,
        helixHeaders: Map<String, String>,
        videoId: String?,
        title: String?,
        uploadDate: String?,
        durationSeconds: Int?,
        type: String?,
        animatedPreviewUrl: String?,
        channelId: String?,
        channelLogin: String?,
        channelName: String?,
        channelImage: String?,
        thumbnail: String?,
        gameId: String?,
        gameSlug: String?,
        gameName: String?,
    ) {
        scope.launch {
            save(
                filesDir = filesDir,
                gqlHeaders = gqlHeaders,
                helixHeaders = helixHeaders,
                videoId = videoId,
                title = title,
                uploadDate = uploadDate,
                durationSeconds = durationSeconds,
                type = type,
                animatedPreviewUrl = animatedPreviewUrl,
                channelId = channelId,
                channelLogin = channelLogin,
                channelName = channelName,
                channelImage = channelImage,
                thumbnail = thumbnail,
                gameId = gameId,
                gameSlug = gameSlug,
                gameName = gameName,
            )
        }
    }

    /**
     * Bookmark toggle for the player, which only knows the individual fields. Note the historical
     * `durationSeconds.toString()` — unlike the [Video] overload, `null` becomes the string "null".
     */
    suspend fun save(
        filesDir: String,
        gqlHeaders: Map<String, String>,
        helixHeaders: Map<String, String>,
        videoId: String?,
        title: String?,
        uploadDate: String?,
        durationSeconds: Int?,
        type: String?,
        animatedPreviewUrl: String?,
        channelId: String?,
        channelLogin: String?,
        channelName: String?,
        channelImage: String?,
        thumbnail: String?,
        gameId: String?,
        gameSlug: String?,
        gameName: String?,
    ) {
        val existing = videoId?.let { bookmarksRepository.getByVideoId(it) }
        if (existing != null) {
            bookmarksRepository.delete(existing)
            return
        }
        val bookmark = bookmarkOf(
            filesDir = filesDir,
            videoId = videoId,
            title = title,
            uploadDate = uploadDate,
            duration = durationSeconds.toString(),
            type = type,
            animatedPreviewUrl = animatedPreviewUrl,
            channelId = channelId,
            channelLogin = channelLogin,
            channelName = channelName,
            channelImage = channelImage,
            thumbnail = thumbnail,
            gameId = gameId,
            gameSlug = gameSlug,
            gameName = gameName,
            gqlHeaders = gqlHeaders,
            helixHeaders = helixHeaders,
        )
        bookmarksRepository.save(bookmark)
    }

    private suspend fun bookmarkOf(
        filesDir: String,
        videoId: String?,
        title: String?,
        uploadDate: String?,
        duration: String?,
        type: String?,
        animatedPreviewUrl: String?,
        channelId: String?,
        channelLogin: String?,
        channelName: String?,
        channelImage: String?,
        thumbnail: String?,
        gameId: String?,
        gameSlug: String?,
        gameName: String?,
        gqlHeaders: Map<String, String>,
        helixHeaders: Map<String, String>,
    ): Bookmark {
        val downloadedThumbnail = download(videoId, thumbnail, "thumbnails", filesDir)
        val downloadedLogo = download(channelId, channelImage, "profile_pics", filesDir)
        val user = loadUserType(channelId, gqlHeaders, helixHeaders)
        return Bookmark(
            videoId = videoId,
            userId = channelId,
            userLogin = channelLogin,
            userName = channelName,
            userType = user?.type,
            userBroadcasterType = user?.broadcasterType,
            userLogo = downloadedLogo,
            gameId = gameId,
            gameSlug = gameSlug,
            gameName = gameName,
            title = title,
            createdAt = uploadDate,
            thumbnail = downloadedThumbnail,
            type = type,
            duration = duration,
            animatedPreviewURL = animatedPreviewUrl,
        )
    }

    /**
     * Returns the destination path immediately and writes the bytes in the background, matching the
     * original copy-paste: the bookmark stores the path even if the download later fails, and a blank
     * URL still creates the directory.
     */
    private fun download(id: String?, url: String?, subdirectory: String, filesDir: String): String? {
        if (id.isNullOrBlank()) return null
        if (url.isNullOrBlank()) return null
        File(filesDir, subdirectory).mkdir()
        val path = filesDir + File.separator + subdirectory + File.separator + id
        scope.launch(Dispatchers.IO) {
            try {
                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
            } catch (e: Exception) {

            }
        }
        return path
    }

    private suspend fun loadUserType(channelId: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>): User? {
        if (channelId.isNullOrBlank()) return null
        return try {
            val response = graphQLRepository.loadQueryUsersType(gqlHeaders, listOf(channelId))
            response.data!!.users?.firstOrNull()?.let {
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
            }
        } catch (e: Exception) {
            if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                try {
                    helixRepository.getUsers(
                        headers = helixHeaders,
                        ids = listOf(channelId)
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
