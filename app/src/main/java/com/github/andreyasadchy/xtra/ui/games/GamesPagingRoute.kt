package com.github.andreyasadchy.xtra.ui.games

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.collections.GameCollectionRow
import com.github.andreyasadchy.xtra.ui.paging.PagingScaffold
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun GamesPagingRoute(
    flow: Flow<PagingData<Game>>,
    refreshSignal: StateFlow<Int>,
    scrollTopSignal: StateFlow<Int>,
    onGameClick: (Game) -> Unit,
    onTagClick: (Tag) -> Unit,
    showTags: Boolean,
    viewersLabel: (Int) -> String,
    showBroadcasters: Boolean,
    broadcastersLabel: (Int) -> String,
    onIntegrityFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = flow.collectAsLazyPagingItems()
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val preferences = LocalContext.current.prefs()
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val columns = preferences.getString(if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT, if (portrait) "1" else "2")?.toIntOrNull() ?: 1
    val refreshTick by refreshSignal.collectAsState()
    val scrollTick by scrollTopSignal.collectAsState()
    LaunchedEffect(refreshTick) { if (refreshTick > 0) items.refresh() }
    LaunchedEffect(scrollTick) {
        if (scrollTick > 0) {
            try {
                if (state.layoutInfo.totalItemsCount > 0) {
                    state.scrollToItem(0)
                }
            } catch (_: IndexOutOfBoundsException) {
                // List was cleared by a concurrent refresh, nothing to scroll to.
            } catch (_: IllegalArgumentException) {
                // Same race: index no longer valid after refresh.
            }
        }
    }
    val errors = listOf(items.loadState.refresh, items.loadState.append, items.loadState.prepend).filterIsInstance<LoadState.Error>()
    val integrityError = errors.firstOrNull { it.error.message == C.FAILED_INTEGRITY_CHECK }
    LaunchedEffect(integrityError) { if (integrityError != null) onIntegrityFailed() }
    PagingScaffold(
        itemCount = items.itemCount,
        refreshing = items.loadState.refresh is LoadState.Loading,
        loadingMore = items.loadState.append is LoadState.Loading || items.loadState.prepend is LoadState.Loading,
        errorText = errors.firstOrNull()?.let { stringResource(R.string.error, it.error.message.orEmpty()) },
        emptyText = stringResource(R.string.nothing_here),
        retryText = stringResource(R.string.retry),
        scrollTopText = stringResource(R.string.scroll_top),
        state = state,
        columns = columns,
        onRefresh = { items.refresh() },
        onRetry = { items.retry() },
        onScrollTop = {
            scope.launch {
                try {
                    if (state.layoutInfo.totalItemsCount > 0) {
                        state.scrollToItem(0)
                    }
                } catch (_: IndexOutOfBoundsException) {
                    // Concurrent refresh cleared the list.
                } catch (_: IllegalArgumentException) {
                    // Concurrent refresh made the index invalid.
                }
            }
        },
        enableScrollTop = preferences.getBoolean(C.UI_SCROLL_TOP, true),
        itemKey = { index ->
            // items.itemKey { ... } internally uses peek(index), which throws
            // IndexOutOfBoundsException when a refresh shrinks the snapshot while
            // LazyGrid is still resolving keys for the old layout (e.g. index 30
            // of size 30 during scroll-to-top + pull-to-refresh). Guard it.
            try {
                if (index < items.itemCount) {
                    items.peek(index)?.let { it.id ?: it.name ?: it.hashCode().toString() } ?: "placeholder:$index"
                } else {
                    "placeholder:$index"
                }
            } catch (_: IndexOutOfBoundsException) {
                "placeholder:$index"
            }
        },
        modifier = modifier,
    ) { index ->
        items[index]?.let { game ->
            GameCollectionRow(
                name = game.name,
                image = game.boxArt,
                viewers = game.viewerCount?.let(viewersLabel),
                broadcasters = game.broadcasterCount?.takeIf { showBroadcasters }?.let(broadcastersLabel),
                tags = if (showTags) game.tags.orEmpty().filter { it.name != null } else emptyList(),
                tagLabel = { it.name.orEmpty() },
                tagEnabled = { it.id != null },
                onTagClick = onTagClick,
                onClick = { onGameClick(game) },
            )
        }
    }
}
