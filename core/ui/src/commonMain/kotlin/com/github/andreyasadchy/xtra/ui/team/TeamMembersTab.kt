package com.github.andreyasadchy.xtra.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.collections.TeamMemberCollectionRow
import com.github.andreyasadchy.xtra.ui.common.PagingGrid
import com.github.andreyasadchy.xtra.ui.common.displayName
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchFormats
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Team member list: live members show their title, game, viewer count, uptime
 * and tags, offline members just their name. A member counts as live when it
 * has a viewer count, as the View row did.
 *
 * [onClick] receives whether the member is live so the host can start the stream
 * instead of opening the channel.
 */
@Composable
fun TeamMembersTab(
    flow: Flow<PagingData<Stream>>,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    parentScrollTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (Stream, Boolean) -> Unit,
    onChannelClick: (Stream) -> Unit,
    onGameClick: (Stream) -> Unit,
    onTagClick: (String) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    val settings = LocalXtraSettings.current
    PagingGrid(
        flow = flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.channelId ?: it.id ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        portrait = portrait,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
        parentScrollTop = parentScrollTop,
        modifier = modifier,
    ) { stream ->
        val live = stream.viewerCount != null
        val uptime = if (live && settings.getBoolean(C.UI_UPTIME, true)) {
            stream.createdAt?.let { Instant.parseOrNull(it) }
                ?.takeIf { it.toEpochMilliseconds() > 0 }
                ?.let { Clock.System.now() - it }
                ?.takeIf { it.isPositive() }
                ?.let { TwitchFormats.formatElapsedTime(it.inWholeSeconds) }
        } else null
        TeamMemberCollectionRow(
            name = displayName(stream.channelName, stream.channelLogin, settings.getString(C.UI_NAME_DISPLAY, "0") ?: "0"),
            image = stream.channelImage,
            roundImage = settings.getBoolean(C.UI_ROUND_USER_IMAGE, true),
            title = stream.title?.trim()?.takeIf { live && it.isNotBlank() },
            game = stream.gameName.takeIf { live },
            viewers = stream.viewerCount?.let { TwitchFormats.formatCount(it, settings.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)) },
            uptime = uptime,
            tags = stream.tags.orEmpty().takeIf { live && settings.getBoolean(C.UI_TAGS, true) }.orEmpty(),
            onClick = { onClick(stream, live) },
            onChannelClick = { onChannelClick(stream) },
            onGameClick = { onGameClick(stream) },
            onTagClick = onTagClick,
        )
    }
}
