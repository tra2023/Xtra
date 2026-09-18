package com.github.andreyasadchy.xtra.ui.common

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.media.MediaRow
import com.github.andreyasadchy.xtra.ui.media.MediaRowAction
import com.github.andreyasadchy.xtra.ui.media.MediaRowData
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.formatChatDate
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Instant

/**
 * Shared video row for the Compose paging lists: resume position, bookmark
 * state, download/share actions and [MediaRow] styling prefs.
 */
@Composable
fun VideoListItem(
    video: Video,
    positions: List<VideoPosition>?,
    bookmarked: Boolean,
    showGame: Boolean = true,
    showChannel: Boolean = true,
    onDownload: (Video) -> Unit,
    onBookmark: (Video) -> Unit,
    onChannelClick: (Video) -> Unit,
    onGameClick: (Video) -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.prefs()
    val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
    val position = video.id?.toLongOrNull()?.let { id -> positions?.find { it.id == id }?.position }
    val durationSeconds = video.durationSeconds
    val startFromBeginning = position != null && durationSeconds != null && durationSeconds > 0 && position >= durationSeconds.toLong() * 1000L
    val open = {
        (context as? MainActivity)?.startVideo(video, if (startFromBeginning) 0L else position, startFromBeginning)
        Unit
    }
    val download = { onDownload(video) }
    val channelName = video.channelName?.let { name ->
        if (video.channelLogin != null && !video.channelLogin.equals(name, true)) {
            when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                "0" -> "$name(${video.channelLogin})"
                "1" -> name
                else -> video.channelLogin
            }
        } else {
            name
        }
    }
    val actions = buildList {
        add(MediaRowAction(context.getString(R.string.download), download))
        if (!video.id.isNullOrBlank()) {
            add(MediaRowAction(context.getString(if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark)) {
                onBookmark(video)
            })
        }
        add(MediaRowAction(context.getString(R.string.share)) {
            context.startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/videos/${video.id}")
                video.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                type = "text/plain"
            }, null))
        })
        if (position != null && position > 0 && !startFromBeginning) {
            add(MediaRowAction(context.getString(R.string.resume), open))
        }
    }
    MediaRow(
        data = MediaRowData(
            thumbnail = video.thumbnail,
            title = video.title?.takeIf { it.isNotBlank() }?.trim(),
            channelName = channelName.takeIf { showChannel },
            channelImage = video.channelImage.takeIf { showChannel },
            gameName = video.gameName.takeIf { showGame },
            date = video.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { formatChatDate(it) },
            views = video.viewCount?.let { count ->
                context.resources.getQuantityString(R.plurals.views, count, TwitchApiHelper.formatCount(count, prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)))
            },
            duration = durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
            type = video.type?.let { TwitchApiHelper.getType(context, it) },
            progress = if (position != null && durationSeconds != null && durationSeconds > 0) {
                (position / (durationSeconds.toLong() * 10L)).coerceIn(0L, 100L) / 100f
            } else {
                null
            },
        ),
        optionsText = context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
        downloadText = context.getString(R.string.download),
        actions = actions,
        onOpen = open,
        onDownload = download,
        onChannelClick = { onChannelClick(video) },
        onGameClick = { onGameClick(video) },
        roundChannelImage = prefs.getBoolean(C.UI_ROUND_USER_IMAGE, true),
        cardMargin = if (!material3) 0.dp else if (prefs.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
        cornerRadius = if (!material3) 0.dp else when (prefs.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
            "1" -> 9.dp
            "2" -> 0.dp
            else -> 12.dp
        },
        compactText = material3 && prefs.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
        material3 = material3,
    )
}
