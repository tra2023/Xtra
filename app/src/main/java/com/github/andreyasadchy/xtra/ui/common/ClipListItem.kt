package com.github.andreyasadchy.xtra.ui.common

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
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
    val context = LocalContext.current
    val prefs = context.prefs()
    val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
    val channelLogin = clip.channelLogin
    val channelName = clip.channelName?.let { name ->
        if (channelLogin != null && !channelLogin.equals(name, true)) {
            when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                "0" -> "$name($channelLogin)"
                "1" -> name
                else -> channelLogin
            }
        } else {
            name
        }
    }
    val download = { onDownload(clip) }
    val open = {
        (context as? MainActivity)?.startClip(clip)
        Unit
    }
    MediaRow(
        data = MediaRowData(
            thumbnail = clip.thumbnail,
            title = clip.title?.takeIf { it.isNotBlank() }?.trim(),
            channelName = channelName.takeIf { showChannel },
            channelImage = clip.channelImage.takeIf { showChannel },
            gameName = clip.gameName.takeIf { showGame },
            date = clip.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { formatChatDate(it) },
            views = clip.viewCount?.let { count ->
                context.resources.getQuantityString(R.plurals.views, count, TwitchApiHelper.formatCount(count, prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)))
            },
            duration = clip.durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
        ),
        optionsText = context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
        downloadText = context.getString(R.string.download),
        actions = listOf(
            MediaRowAction(context.getString(R.string.download), download),
            MediaRowAction(context.getString(R.string.share)) {
                context.startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/${clip.channelLogin}/clip/${clip.id}")
                    clip.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    type = "text/plain"
                }, null))
            },
        ),
        onOpen = open,
        onDownload = download,
        onChannelClick = { onChannelClick(clip) },
        onGameClick = { onGameClick(clip) },
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
