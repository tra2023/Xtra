package com.github.andreyasadchy.xtra.util.chat

import kotlinx.serialization.json.Json

/**
 * Video metadata embedded in downloaded chat JSON files (`{"video": {...}}`).
 *
 * Platform-agnostic port of the `JsonReader` blocks triplicated in
 * `DownloadsViewModel.updateChatUrl`, `SavedPagerViewModel.saveFolders` and
 * `SavedPagerViewModel.saveVideos`: same keys, same blank-means-absent
 * semantics, but tolerant per-field (one malformed value no longer discards
 * the whole file).
 */
data class ChatVideoMetadata(
    val id: String? = null,
    val title: String? = null,
    val uploadDate: Long? = null,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    val gameId: String? = null,
    val gameSlug: String? = null,
    val gameName: String? = null,
)

private val chatJson = Json { ignoreUnknownKeys = true }

fun parseVideoMetadataFromChatJson(json: String): ChatVideoMetadata? {
    return try {
        val video = chatJson.parseToJsonElement(json)
            .asObjectOrNullCompat()
            ?.get("video")
            .asObjectOrNullCompat()
            ?: return null
        ChatVideoMetadata(
            id = video.stringOrNullCompat("id"),
            title = video.stringOrNullCompat("title"),
            uploadDate = video.longOrNullCompat("uploadDate"),
            channelId = video.stringOrNullCompat("channelId"),
            channelLogin = video.stringOrNullCompat("channelLogin"),
            channelName = video.stringOrNullCompat("channelName"),
            gameId = video.stringOrNullCompat("gameId"),
            gameSlug = video.stringOrNullCompat("gameSlug"),
            gameName = video.stringOrNullCompat("gameName"),
        )
    } catch (e: Exception) {
        null
    }
}
