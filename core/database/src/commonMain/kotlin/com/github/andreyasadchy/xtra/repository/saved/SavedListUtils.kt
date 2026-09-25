package com.github.andreyasadchy.xtra.repository.saved

import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Platform-agnostic helpers for the Saved screens, ported from
 * `app/.../ui/saved/bookmarks/BookmarksFragment.kt` and
 * `app/.../ui/saved/downloads/DownloadsAdapter.kt`.
 *
 * Sort constants are duplicated as plain strings so commonMain doesn't depend
 * on the Android-only sort dialogs (same pattern as `TopStreamsBrowseController`).
 */
const val SAVED_SORT_EXPIRES_AT = "expires_at"
const val SAVED_SORT_CREATED_AT = "created_at"
const val SAVED_SORT_SAVED_AT = "saved_at"
const val SAVED_ORDER_ASC = "asc"
const val SAVED_ORDER_DESC = "desc"

fun sortBookmarks(
    list: List<Bookmark>,
    sort: String?,
    order: String?,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): List<Bookmark> {
    return if (order == SAVED_ORDER_ASC) {
        when (sort) {
            SAVED_SORT_EXPIRES_AT -> list.sortedWith(compareBy(nullsLast()) { timeLeftSeconds(it, nowMillis) })
            SAVED_SORT_CREATED_AT -> list.sortedWith(compareBy(nullsLast()) { createdMillis(it) })
            else -> list.sortedWith(compareBy(nullsLast()) { it.id })
        }
    } else {
        when (sort) {
            SAVED_SORT_EXPIRES_AT -> list.sortedWith(compareByDescending(nullsFirst()) { timeLeftSeconds(it, nowMillis) })
            SAVED_SORT_CREATED_AT -> list.sortedWith(compareByDescending(nullsFirst()) { createdMillis(it) })
            else -> list.sortedWith(compareByDescending(nullsFirst()) { it.id })
        }
    }
}

/** Remaining archive lifetime in seconds, or null when it does not expire. */
fun timeLeftSeconds(bookmark: Bookmark, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): Long? {
    if (bookmark.type?.lowercase() != "archive") return null
    val createdAt = bookmark.createdAt ?: return null
    val created = Instant.parseOrNull(createdAt)?.takeIf { it.toEpochMilliseconds() > 0 } ?: return null
    val remaining = (created + retentionDays(bookmark.userType, bookmark.userBroadcasterType).days) -
        Instant.fromEpochMilliseconds(nowMillis)
    return remaining.inWholeSeconds.takeIf { remaining.isPositive() }
}

fun createdMillis(bookmark: Bookmark): Long? =
    bookmark.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }

/** Twitch VOD retention by user type: affiliates 14 days, partners 60, everyone else 7. */
fun retentionDays(userType: String?, broadcasterType: String?): Long {
    val type = userType ?: broadcasterType
    if (type.isNullOrBlank()) return 7
    return when (type.lowercase()) {
        "affiliate" -> 14
        else -> 60 // Partners, Prime, Turbo
    }
}

/** 0..1 progress fraction, matching `DownloadsAdapter.fraction`. */
fun downloadFraction(progress: Int, max: Int): Float =
    if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f

/**
 * Active downloads without a bound-service progress snapshot report as pending,
 * matching `DownloadsAdapter.item`.
 */
fun effectiveDownloadStatus(status: Int, hasProgress: Boolean): Int =
    if (status in listOf(
            OfflineVideo.STATUS_DOWNLOADING,
            OfflineVideo.STATUS_QUEUED,
            OfflineVideo.STATUS_WAITING_FOR_STREAM,
        ) && !hasProgress
    ) {
        OfflineVideo.STATUS_PENDING
    } else {
        status
    }
