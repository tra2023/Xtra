package com.github.andreyasadchy.xtra.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.common.ClipListItem
import com.github.andreyasadchy.xtra.ui.common.PagingGrid
import com.github.andreyasadchy.xtra.ui.common.StreamListItem
import com.github.andreyasadchy.xtra.ui.common.VideoListItem
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.flow.Flow

/**
 * Shared game tab lists for the game screens. Hosts own the ViewModels,
 * signals, navigation and dialogs; rows and paging behavior live here exactly
 * once.
 *
 * Everything platform-specific is a parameter: [flow] instead of a ViewModel,
 * [portrait] instead of `LocalConfiguration`, [modifier] so an Android host can
 * attach `rememberNestedScrollInteropConnection`, and position/bookmark lookups
 * as lambdas so this module stays free of the database types.
 */
@Composable
fun GameStreamsTab(
    flow: Flow<PagingData<Stream>>,
    compact: Boolean,
    enableScrollTop: Boolean,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onStreamClick: (Stream) -> Unit,
    onChannelClick: (Stream) -> Unit,
    onGameClick: (Stream) -> Unit,
    onTagClick: (String) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        enableScrollTop = enableScrollTop,
        keyForItem = { it.id ?: it.channelId ?: it.channelLogin ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        portrait = portrait,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
        modifier = modifier,
    ) { stream ->
        StreamListItem(
            stream = stream,
            compact = compact,
            showGame = false,
            onStreamClick = onStreamClick,
            onChannelClick = onChannelClick,
            onGameClick = onGameClick,
            onTagClick = onTagClick,
        )
    }
}

@Composable
fun GameVideosTab(
    flow: Flow<PagingData<Video>>,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    positionFor: (String?) -> Long? = { null },
    isBookmarked: (String?) -> Boolean = { false },
    onDownload: (Video) -> Unit,
    onBookmark: (Video) -> Unit,
    onChannelClick: (Video) -> Unit,
    onGameClick: (Video) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.id ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        portrait = portrait,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
        modifier = modifier,
    ) { video ->
        VideoListItem(
            video = video,
            position = positionFor(video.id),
            bookmarked = isBookmarked(video.id),
            showGame = false,
            onDownload = onDownload,
            onBookmark = onBookmark,
            onChannelClick = onChannelClick,
            onGameClick = onGameClick,
        )
    }
}

@Composable
fun GameClipsTab(
    flow: Flow<PagingData<Clip>>,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onDownload: (Clip) -> Unit,
    onChannelClick: (Clip) -> Unit,
    onGameClick: (Clip) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.id ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        portrait = portrait,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
        modifier = modifier,
    ) { clip ->
        ClipListItem(
            clip = clip,
            showGame = false,
            onDownload = onDownload,
            onChannelClick = onChannelClick,
            onGameClick = onGameClick,
        )
    }
}

/** Whether the stream rows render in the compact style, from platform settings. */
@Composable
fun streamsCompact(): Boolean =
    LocalXtraSettings.current.getString(C.COMPACT_STREAMS, "disabled") == "all"
