package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.media.MediaRow
import com.github.andreyasadchy.xtra.ui.media.MediaRowAction
import com.github.andreyasadchy.xtra.ui.media.MediaRowData
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchFormats
import com.github.andreyasadchy.xtra.util.formatChatDate
import kotlin.time.Instant

/**
 * Shared video row for the Compose paging lists: resume position, bookmark
 * state, download/share actions and [MediaRow] styling prefs.
 */
@Composable
fun VideoListItem(
    video: Video,
    position: Long?,
    bookmarked: Boolean,
    showGame: Boolean = true,
    showChannel: Boolean = true,
    onDownload: (Video) -> Unit,
    onBookmark: (Video) -> Unit,
    onChannelClick: (Video) -> Unit,
    onGameClick: (Video) -> Unit,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    val actions = LocalXtraMediaActions.current
    val material3 = settings.getBoolean(C.UI_THEME_MATERIAL3, true)
    val durationSeconds = video.durationSeconds
    val startFromBeginning = position != null && durationSeconds != null && durationSeconds > 0 && position >= durationSeconds.toLong() * 1000L
    val open = { actions.openVideo(video, if (startFromBeginning) 0L else position, startFromBeginning) }
    val download = { onDownload(video) }
    val channelName = displayName(
        video.channelName,
        video.channelLogin,
        settings.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
    )
    val rowActions = buildList {
        add(MediaRowAction(strings.download, download))
        if (!video.id.isNullOrBlank()) {
            add(MediaRowAction(if (bookmarked) strings.removeBookmark else strings.addBookmark) {
                onBookmark(video)
            })
        }
        add(MediaRowAction(strings.share) {
            actions.share("https://twitch.tv/videos/${video.id}", video.title)
        })
        if (position != null && position > 0 && !startFromBeginning) {
            add(MediaRowAction(strings.resume, open))
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
            views = video.viewCount?.let { strings.views(it) },
            duration = durationSeconds?.let { TwitchFormats.formatElapsedTime(it.toLong()) },
            type = strings.videoType(video.type),
            progress = if (position != null && durationSeconds != null && durationSeconds > 0) {
                (position / (durationSeconds.toLong() * 10L)).coerceIn(0L, 100L) / 100f
            } else {
                null
            },
        ),
        optionsText = strings.options,
        downloadText = strings.download,
        actions = rowActions,
        onOpen = open,
        onDownload = download,
        onChannelClick = { onChannelClick(video) },
        onGameClick = { onGameClick(video) },
        roundChannelImage = settings.getBoolean(C.UI_ROUND_USER_IMAGE, true),
        cardMargin = if (!material3) 0.dp else if (settings.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
        cornerRadius = if (!material3) 0.dp else when (settings.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
            "1" -> 9.dp
            "2" -> 0.dp
            else -> 12.dp
        },
        compactText = material3 && settings.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
        material3 = material3,
    )
}
