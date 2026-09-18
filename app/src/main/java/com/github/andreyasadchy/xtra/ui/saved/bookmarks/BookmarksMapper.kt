package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import android.content.Context
import android.text.format.DateUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.BookmarkIgnoredUser
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.bookmarks.BookmarkListItem
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.formatChatDate
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Maps a saved [Bookmark] to the shared [BookmarkListItem] row and performs its
 * actions. The list itself is Compose; the platform formatting, preferences and
 * navigation stay here, mirroring [com.github.andreyasadchy.xtra.ui.saved.downloads.DownloadsAdapter].
 */
class BookmarksMapper(
    private val fragment: Fragment,
    private val refreshVideo: (String?) -> Unit,
    private val showDownloadDialog: (Video) -> Unit,
    private val vodIgnoreUser: (String) -> Unit,
    private val deleteVideo: (Bookmark) -> Unit,
) {

    fun item(bookmark: Bookmark, positions: List<VideoPosition>?, ignored: List<BookmarkIgnoredUser>?): BookmarkListItem {
        val context = fragment.requireContext()
        val durationSeconds = durationSeconds(bookmark)
        val position = position(bookmark, positions)
        val ignore = ignored?.find { it.userId == bookmark.userId } != null
        return BookmarkListItem(
            id = bookmark.id,
            title = bookmark.title?.trim()?.takeIf { it.isNotBlank() },
            thumbnail = bookmark.thumbnail,
            date = bookmark.createdAt
                ?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }
                ?.let { formatChatDate(it) },
            timeLeft = timeLeft(bookmark, ignore)?.let { context.getString(R.string.vod_time_left, it) },
            duration = durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
            type = bookmark.type?.let { TwitchApiHelper.getType(context, it) },
            channel = if (bookmark.userName != null && bookmark.userLogin != null && !bookmark.userLogin.equals(bookmark.userName, true)) {
                when (context.prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                    "0" -> "${bookmark.userName}(${bookmark.userLogin})"
                    "1" -> bookmark.userName
                    else -> bookmark.userLogin
                }
            } else bookmark.userName,
            channelImage = bookmark.userLogo,
            roundImage = context.prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true),
            game = bookmark.gameName,
            watched = if (position != null && durationSeconds != null && durationSeconds > 0) {
                (position.toFloat() / (durationSeconds * 1000f)).coerceIn(0f, 1f)
            } else null,
            actions = actions(bookmark, ignore, context),
        )
    }

    fun open(bookmark: Bookmark, positions: List<VideoPosition>?) {
        val durationSeconds = durationSeconds(bookmark)
        val position = position(bookmark, positions)
        val startFromBeginning = startFromBeginning(position, durationSeconds)
        (fragment.activity as MainActivity).startVideo(
            video(bookmark, durationSeconds),
            if (startFromBeginning) 0 else position,
            startFromBeginning,
        )
    }

    fun channel(bookmark: Bookmark) {
        fragment.findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = bookmark.userId,
                channelLogin = bookmark.userLogin,
                channelName = bookmark.userName,
                channelImage = bookmark.userLogo,
                updateLocal = true,
            )
        )
    }

    fun game(bookmark: Bookmark) {
        val context = fragment.requireContext()
        fragment.findNavController().navigate(
            if (context.prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = bookmark.gameId,
                    gameSlug = bookmark.gameSlug,
                    gameName = bookmark.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = bookmark.gameId,
                    gameSlug = bookmark.gameSlug,
                    gameName = bookmark.gameName,
                )
            }
        )
    }

    fun action(bookmark: Bookmark, action: Int) {
        when (action) {
            R.id.delete -> deleteVideo(bookmark)
            R.id.download -> showDownloadDialog(video(bookmark, durationSeconds(bookmark)))
            R.id.vodIgnore -> bookmark.userId?.let { vodIgnoreUser(it) }
            R.id.refresh -> refreshVideo(bookmark.videoId)
        }
    }

    private fun video(bookmark: Bookmark, durationSeconds: Int?) = Video(
        id = bookmark.videoId,
        channelId = bookmark.userId,
        channelLogin = bookmark.userLogin,
        channelName = bookmark.userName,
        channelImageURL = bookmark.userLogo,
        gameId = bookmark.gameId,
        gameSlug = bookmark.gameSlug,
        gameName = bookmark.gameName,
        title = bookmark.title,
        thumbnailURL = bookmark.thumbnail,
        createdAt = bookmark.createdAt,
        durationSeconds = durationSeconds,
        type = bookmark.type,
        animatedPreviewURL = bookmark.animatedPreviewURL,
    )

    private fun actions(bookmark: Bookmark, ignore: Boolean, context: Context): List<Pair<Int, String>> = buildList {
        if (!bookmark.videoId.isNullOrBlank()) {
            add(R.id.refresh to context.getString(R.string.refresh))
            add(R.id.download to context.getString(R.string.download))
        }
        if (bookmark.type?.lowercase() == "archive" && bookmark.userId != null && context.prefs().getBoolean(C.UI_BOOKMARK_TIME_LEFT, true)) {
            add(R.id.vodIgnore to context.getString(if (ignore) R.string.vod_remove_ignore else R.string.vod_ignore_user))
        }
        add(R.id.delete to context.getString(R.string.delete))
    }

    /** Remaining archive lifetime, using Twitch's retention per user type. */
    private fun timeLeft(bookmark: Bookmark, ignore: Boolean): String? {
        val context = fragment.requireContext()
        if (ignore || bookmark.type?.lowercase() != "archive") return null
        if (!context.prefs().getBoolean(C.UI_BOOKMARK_TIME_LEFT, true)) return null
        val createdAt = bookmark.createdAt ?: return null
        val created = Instant.parseOrNull(createdAt)?.takeIf { it.toEpochMilliseconds() > 0 } ?: return null
        val userType = bookmark.userType ?: bookmark.userBroadcasterType
        val days = if (userType.isNullOrBlank()) {
            7
        } else {
            when (userType.lowercase()) {
                "affiliate" -> 14
                else -> 60 // Partners, Prime, Turbo
            }
        }
        val remaining = (created + days.days) - Clock.System.now()
        return if (remaining.isPositive()) {
            TwitchApiHelper.getDurationFromSeconds(context, remaining.inWholeSeconds.toString())
        } else null
    }

    private fun durationSeconds(bookmark: Bookmark): Int? =
        bookmark.duration?.let { duration -> duration.toIntOrNull() ?: TwitchApiHelper.getDuration(duration) }

    private fun position(bookmark: Bookmark, positions: List<VideoPosition>?): Long? =
        bookmark.videoId?.toLongOrNull()?.let { id -> positions?.find { it.id == id }?.position }

    private fun startFromBeginning(position: Long?, durationSeconds: Int?): Boolean =
        position != null && durationSeconds != null && durationSeconds > 0 && position >= (durationSeconds * 1000)
}
