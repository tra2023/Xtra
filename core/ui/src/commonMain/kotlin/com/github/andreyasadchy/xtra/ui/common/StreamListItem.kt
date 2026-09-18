package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.browse.StreamCard
import com.github.andreyasadchy.xtra.ui.browse.streamCardState
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchFormats
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Shared stream row for the Compose paging lists: formatted viewers/uptime
 * labels, card styling prefs and [streamCardState].
 */
@Composable
fun StreamListItem(
    stream: Stream,
    compact: Boolean,
    showGame: Boolean = true,
    onStreamClick: (Stream) -> Unit,
    onChannelClick: (Stream) -> Unit,
    onGameClick: (Stream) -> Unit,
    onTagClick: (String) -> Unit,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    val viewers = stream.viewerCount?.let { count ->
        val formatted = TwitchFormats.formatCount(count, settings.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true))
        if (compact) formatted else strings.viewers(count)
    }
    val uptime = if (settings.getBoolean(C.UI_UPTIME, true)) {
        stream.createdAt?.let { Instant.parseOrNull(it) }
            ?.takeIf { it.toEpochMilliseconds() > 0 }
            ?.let { Clock.System.now() - it }
            ?.takeIf { it.isPositive() }
            ?.let { TwitchFormats.formatElapsedTime(it.inWholeSeconds) }
            ?.let { if (compact) it else strings.uptime(it) }
    } else null
    val material3 = settings.getBoolean(C.UI_THEME_MATERIAL3, true)
    StreamCard(
        state = streamCardState(
            stream = stream,
            nameDisplay = settings.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            showGame = showGame,
            showTags = settings.getBoolean(C.UI_TAGS, true),
            viewers = viewers,
            uptime = uptime,
        ),
        onStreamClick = { onStreamClick(stream) },
        onChannelClick = { onChannelClick(stream) },
        onGameClick = { onGameClick(stream) },
        onTagClick = onTagClick,
        compact = compact,
        roundUserImage = settings.getBoolean(C.UI_ROUND_USER_IMAGE, true),
        cardMargin = if (!material3) 0.dp else if (settings.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
        cornerRadius = if (!material3) 0.dp else when (settings.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
            "1" -> 9.dp
            "2" -> 0.dp
            else -> 12.dp
        },
        compactText = material3 && settings.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
    )
}
