package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.flow.Flow

/**
 * The paging list body for each media type, defined once and reused by every
 * host that shows one (game screens, Following, channel, search, top).
 *
 * Hosts own the ViewModels, signals, navigation and dialogs; everything that is
 * platform-specific arrives as a parameter: [flow] instead of a ViewModel,
 * [portrait] instead of `LocalConfiguration`, and [modifier] so an Android host
 * can attach `rememberNestedScrollInteropConnection`.
 */
@Composable
fun StreamsTab(
    flow: Flow<PagingData<Stream>>,
    compact: Boolean,
    showGame: Boolean,
    enableScrollTop: Boolean,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    parentScrollTop: (() -> Unit)? = null,
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
        parentScrollTop = parentScrollTop,
        modifier = modifier,
    ) { stream ->
        StreamListItem(
            stream = stream,
            compact = compact,
            showGame = showGame,
            onStreamClick = onStreamClick,
            onChannelClick = onChannelClick,
            onGameClick = onGameClick,
            onTagClick = onTagClick,
        )
    }
}

@Composable
fun VideosTab(
    flow: Flow<PagingData<Video>>,
    showGame: Boolean,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    parentScrollTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showChannel: Boolean = true,
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
        parentScrollTop = parentScrollTop,
        modifier = modifier,
    ) { video ->
        VideoListItem(
            video = video,
            position = positionFor(video.id),
            bookmarked = isBookmarked(video.id),
            showGame = showGame,
            showChannel = showChannel,
            onDownload = onDownload,
            onBookmark = onBookmark,
            onChannelClick = onChannelClick,
            onGameClick = onGameClick,
        )
    }
}

@Composable
fun ClipsTab(
    flow: Flow<PagingData<Clip>>,
    showGame: Boolean,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    parentScrollTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showChannel: Boolean = true,
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
        parentScrollTop = parentScrollTop,
        modifier = modifier,
    ) { clip ->
        ClipListItem(
            clip = clip,
            showGame = showGame,
            showChannel = showChannel,
            onDownload = onDownload,
            onChannelClick = onChannelClick,
            onGameClick = onGameClick,
        )
    }
}

@Composable
fun GamesTab(
    flow: Flow<PagingData<Game>>,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    parentScrollTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    followLabels: (Game) -> List<String> = { emptyList() },
    diskCache: Boolean = false,
    onTagClick: (Tag) -> Unit,
    onClick: (Game) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.id ?: it.name ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        portrait = portrait,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
        parentScrollTop = parentScrollTop,
        modifier = modifier,
    ) { game ->
        GameListItem(
            game = game,
            onTagClick = onTagClick,
            onClick = onClick,
            followLabels = followLabels(game),
            diskCache = diskCache,
        )
    }
}

@Composable
fun ChannelsTab(
    flow: Flow<PagingData<User>>,
    bottomInset: Dp,
    portrait: Boolean,
    refreshTick: Int,
    scrollTick: Int,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    parentScrollTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    detailsFor: (User) -> List<String> = { emptyList() },
    labelsFor: (User) -> List<String> = { emptyList() },
    onClick: (User) -> Unit,
    onIntegrityFailed: () -> Unit,
) {
    PagingGrid(
        flow = flow,
        refreshSignal = refreshTick,
        retrySignal = 0,
        scrollTopSignal = scrollTick,
        keyForItem = { it.id ?: it.login ?: it.hashCode().toString() },
        bottomInset = bottomInset,
        portrait = portrait,
        onIntegrityFailed = onIntegrityFailed,
        onAtTopChanged = onAtTopChanged,
        parentScrollTop = parentScrollTop,
        modifier = modifier,
    ) { user ->
        UserListItem(
            user = user,
            details = detailsFor(user),
            labels = labelsFor(user),
            onClick = onClick,
        )
    }
}

/**
 * Whether stream rows render in the compact style, from platform settings.
 *
 * The `compactStreams` preference means "all", "followed" or "disabled", so
 * followed-only lists (Following) compact for either enabled value, while the
 * other screens compact only for "all".
 */
@Composable
fun streamsCompact(followedContent: Boolean): Boolean {
    val mode = LocalXtraSettings.current.getString(C.COMPACT_STREAMS, "disabled")
    return if (followedContent) mode != "disabled" else mode == "all"
}
