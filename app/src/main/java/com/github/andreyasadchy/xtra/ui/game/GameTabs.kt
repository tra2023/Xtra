package com.github.andreyasadchy.xtra.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.common.ClipListItem
import com.github.andreyasadchy.xtra.ui.common.PagingGrid
import com.github.andreyasadchy.xtra.ui.common.StreamListItem
import com.github.andreyasadchy.xtra.ui.common.VideoListItem
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsViewModel
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsViewModel
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Shared game tab lists for the game pager screens. Hosts own the ViewModels,
 * signals, navigation and dialogs; rows and paging behavior live here exactly
 * once.
 */
@Composable
fun GameStreamsTab(
    viewModel: GameStreamsViewModel,
    compact: Boolean,
    enableScrollTop: Boolean,
    bottomInset: Dp,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    onStreamClick: (Stream) -> Unit,
    onChannelClick: (Stream) -> Unit,
    onGameClick: (Stream) -> Unit,
    onTagClick: (String) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = viewModel.flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        enableScrollTop = enableScrollTop,
        keyForItem = { it.id ?: it.channelId ?: it.channelLogin ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
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
    viewModel: GameVideosViewModel,
    bottomInset: Dp,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    onDownload: (Video) -> Unit,
    onBookmark: (Video) -> Unit,
    onChannelClick: (Video) -> Unit,
    onGameClick: (Video) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    val positions by viewModel.positions.collectAsState(initial = null)
    val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
    val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
    PagingGrid(
        flow = viewModel.flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.id ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
    ) { video ->
        VideoListItem(
            video = video,
            positions = positions,
            bookmarked = video.id in bookmarkIds,
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
    viewModel: GameClipsViewModel,
    bottomInset: Dp,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    onDownload: (Clip) -> Unit,
    onChannelClick: (Clip) -> Unit,
    onGameClick: (Clip) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = viewModel.flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.id ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
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

@Composable
fun streamsCompact(): Boolean =
    LocalContext.current.prefs().getString(C.COMPACT_STREAMS, "disabled") == "all"
