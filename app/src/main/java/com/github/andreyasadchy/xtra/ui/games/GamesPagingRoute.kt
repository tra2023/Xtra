package com.github.andreyasadchy.xtra.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.shared.browse.GamesGridContent
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Android host for the shared games grid. Owns everything the old
 * RecyclerView + `PagedListFragment.initializeAdapter` used to own:
 * refresh/append load states, empty + error UI with retry, integrity-token
 * recovery, and refresh / scroll-top signals from the Fragment. Data still
 * comes from [GamesViewModel.flow] (shared headers path); rows render with
 * shared [GamesGridContent].
 */
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
    val lazyGames = flow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val refreshTick by refreshSignal.collectAsState()
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) lazyGames.refresh()
    }
    val scrollTick by scrollTopSignal.collectAsState()
    LaunchedEffect(scrollTick) {
        if (scrollTick > 0) gridState.scrollToItem(0)
    }

    val refreshState = lazyGames.loadState.refresh
    val appendState = lazyGames.loadState.append
    val integrityError = listOf(
        lazyGames.loadState.refresh,
        lazyGames.loadState.prepend,
        lazyGames.loadState.append,
    ).filterIsInstance<LoadState.Error>()
        .firstOrNull { it.error.message == C.FAILED_INTEGRITY_CHECK }
    LaunchedEffect(integrityError) {
        if (integrityError != null) onIntegrityFailed()
    }

    PullToRefreshBox(
        isRefreshing = refreshState is LoadState.Loading && lazyGames.itemCount > 0,
        onRefresh = { lazyGames.refresh() },
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
        when {
            refreshState is LoadState.Loading && lazyGames.itemCount == 0 -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            refreshState is LoadState.Error && lazyGames.itemCount == 0 -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.error, refreshState.error.message.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { lazyGames.retry() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            lazyGames.itemCount == 0 -> {
                Text(
                    text = stringResource(R.string.nothing_here),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                GamesGridContent(
                    games = lazyGames.itemSnapshotList.items,
                    onGameClick = onGameClick,
                    onTagClick = onTagClick,
                    showTags = showTags,
                    viewersLabel = viewersLabel,
                    showBroadcasters = showBroadcasters,
                    broadcastersLabel = broadcastersLabel,
                    gridState = gridState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Footer states while items show (replaces the swipeRefresh spinner).
        if (lazyGames.itemCount > 0) {
            when {
                appendState is LoadState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
                appendState is LoadState.Error -> {
                    Button(
                        onClick = { lazyGames.retry() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        // Scroll-top, same placement as the old Views FAB (top-center).
        val showScrollTop by remember {
            derivedStateOf { gridState.firstVisibleItemIndex > 8 }
        }
        if (showScrollTop && lazyGames.itemCount > 0) {
            SmallFloatingActionButton(
                onClick = { scope.launch { gridState.scrollToItem(0) } },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_arrow_upward_black_24),
                    contentDescription = null,
                )
            }
        }
        }
    }
}
