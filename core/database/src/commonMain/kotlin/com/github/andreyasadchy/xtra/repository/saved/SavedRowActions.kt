package com.github.andreyasadchy.xtra.repository.saved

import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.util.TwitchImageUrls

/**
 * Row decisions for the saved card lists, ported from
 * `app/.../ui/saved/bookmarks/BookmarksMapper.kt` and
 * `app/.../ui/saved/downloads/DownloadsAdapter.kt`.
 *
 * Only decisions live here: which actions a row offers and how progress is
 * derived. Labels, icons and navigation stay in the app, which maps each
 * action to its `R.id` + `getString` pair.
 */
enum class BookmarkRowAction {
    REFRESH,
    DOWNLOAD,
    VOD_IGNORE,
    DELETE,
}

fun bookmarkRowActions(
    videoId: String?,
    type: String?,
    userId: String?,
    ignoreEnabled: Boolean,
): List<BookmarkRowAction> = buildList {
    if (!videoId.isNullOrBlank()) {
        add(BookmarkRowAction.REFRESH)
        add(BookmarkRowAction.DOWNLOAD)
    }
    if (type?.lowercase() == "archive" && userId != null && ignoreEnabled) {
        add(BookmarkRowAction.VOD_IGNORE)
    }
    add(BookmarkRowAction.DELETE)
}

enum class DownloadRowAction {
    STOP,
    RESUME,
    MOVE,
    CONVERT,
    UPDATE_CHAT_URL,
    SHARE,
    DELETE,
}

fun downloadRowActions(
    status: Int,
    live: Boolean,
    shared: Boolean,
    isPlaylist: Boolean,
): List<DownloadRowAction> = buildList {
    when (status) {
        OfflineVideo.STATUS_DOWNLOADING,
        OfflineVideo.STATUS_QUEUED,
        OfflineVideo.STATUS_WAITING_FOR_NETWORK,
        OfflineVideo.STATUS_WAITING_FOR_WIFI,
        OfflineVideo.STATUS_WAITING_FOR_STREAM -> add(DownloadRowAction.STOP)
        OfflineVideo.STATUS_PENDING -> {
            if (live) add(DownloadRowAction.STOP)
            add(DownloadRowAction.RESUME)
        }
        else -> {
            add(DownloadRowAction.MOVE)
            if (isPlaylist) add(DownloadRowAction.CONVERT)
            add(DownloadRowAction.UPDATE_CHAT_URL)
            if (shared) add(DownloadRowAction.SHARE)
        }
    }
    add(DownloadRowAction.DELETE)
}

/** Bookmark duration as seconds: raw seconds or a Twitch `1h2m3s` string. */
fun bookmarkDurationSeconds(duration: String?): Int? =
    duration?.let { it.toIntOrNull() ?: TwitchImageUrls.getDuration(it) }

/** Saved watch position for a video id, if any. */
fun videoPosition(videoId: String?, positions: List<VideoPosition>?): Long? =
    videoId?.toLongOrNull()?.let { id -> positions?.find { it.id == id }?.position }

/** Watch progress in 0..1 from millisecond position/duration. */
fun watchedFraction(position: Long?, durationMs: Long?): Float? =
    if (position != null && durationMs != null && durationMs > 0) {
        (position.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        null
    }

/** Whether playback already reached the end, so it restarts from the beginning. */
fun startsFromBeginning(position: Long?, durationSeconds: Int?): Boolean =
    position != null && durationSeconds != null && durationSeconds > 0 && position >= (durationSeconds * 1000)
