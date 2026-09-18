package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.media.MediaRow
import com.github.andreyasadchy.xtra.ui.media.MediaRowAction
import com.github.andreyasadchy.xtra.ui.media.MediaRowData
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchFormats
import com.github.andreyasadchy.xtra.util.formatChatDate
import kotlin.time.Instant

/**
 * Shared clip row for the Compose paging lists: download/share actions and
 * [MediaRow] styling prefs.
 */
@Composable
fun ClipListItem(
    clip: Clip,
    showGame: Boolean = true,
    showChannel: Boolean = true,
    onDownload: (Clip) -> Unit,
    onChannelClick: (Clip) -> Unit,
    onGameClick: (Clip) -> Unit,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    val actions = LocalXtraMediaActions.current
    val material3 = settings.getBoolean(C.UI_THEME_MATERIAL3, true)
    val channelName = displayName(
        clip.channelName,
        clip.channelLogin,
        settings.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
    )
    val download = { onDownload(clip) }
    val open = { actions.openClip(clip) }
    MediaRow(
        data = MediaRowData(
            thumbnail = clip.thumbnail,
            title = clip.title?.takeIf { it.isNotBlank() }?.trim(),
            channelName = channelName.takeIf { showChannel },
            channelImage = clip.channelImage.takeIf { showChannel },
            gameName = clip.gameName.takeIf { showGame },
            date = clip.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { formatChatDate(it) },
            views = clip.viewCount?.let { strings.views(it) },
            duration = clip.durationSeconds?.let { TwitchFormats.formatElapsedTime(it.toLong()) },
        ),
        optionsText = strings.options,
        downloadText = strings.download,
        actions = listOf(
            MediaRowAction(strings.download, download),
            MediaRowAction(strings.share) {
                actions.share("https://twitch.tv/${clip.channelLogin}/clip/${clip.id}", clip.title)
            },
        ),
        onOpen = open,
        onDownload = download,
        onChannelClick = { onChannelClick(clip) },
        onGameClick = { onGameClick(clip) },
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
