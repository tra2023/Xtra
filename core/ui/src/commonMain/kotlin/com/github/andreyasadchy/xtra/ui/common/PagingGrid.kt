package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.ui.paging.PagingScaffold
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Paging grid without a fragment receiver. [PagedListFragment.PagingContent]
 * delegates here; full-Compose screens call this directly.
 *
 * @param portrait orientation used to resolve the configured column count.
 * @param modifier host-provided modifier (e.g. the Android nested-scroll
 * interop connection).
 */
@Composable
fun <T : Any> PagingGrid(
    flow: Flow<PagingData<T>>,
    refreshSignal: Int,
    retrySignal: Int,
    bottomInset: Dp,
    portrait: Boolean,
    scrollTopSignal: Int = 0,
    enableRefresh: Boolean = true,
    itemKey: ((Int) -> Any)? = null,
    keyForItem: ((T) -> Any?)? = null,
    onIntegrityFailed: () -> Unit,
    onAtTopChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val settings = LocalXtraSettings.current
    val strings = LocalXtraStrings.current
    val items = flow.collectAsLazyPagingItems()
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Per-instance scroller: a shared slot would race with a last-write win
    // when several lists compose at once (pager tabs).
    val scrollTop: () -> Unit = remember(state) {
        {
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
        }
    }
    LaunchedEffect(refreshSignal) { if (refreshSignal > 0) items.refresh() }
    LaunchedEffect(retrySignal) { if (retrySignal > 0) items.retry() }
    if (onAtTopChanged != null) {
        LaunchedEffect(state) {
            snapshotFlow { !state.canScrollBackward }
                .distinctUntilChanged()
                .collect { onAtTopChanged(it) }
        }
    }
    LaunchedEffect(scrollTopSignal) { if (scrollTopSignal > 0) scrollTop() }
    val error = listOf(items.loadState.refresh, items.loadState.append, items.loadState.prepend).filterIsInstance<LoadState.Error>().firstOrNull()
    LaunchedEffect(error) {
        if (error?.error?.message == C.FAILED_INTEGRITY_CHECK) onIntegrityFailed()
    }
    val columns = settings.getString(
        if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT,
        if (portrait) "1" else "2",
    )?.toIntOrNull() ?: 1
    // Stable key lambda: a new instance every recomposition would make the
    // grid drop and re-resolve all item keys on unrelated state changes.
    val resolvedItemKey: (Int) -> Any = remember(items, itemKey, keyForItem) {
        itemKey ?: { index ->
            // items.itemKey { ... } uses peek(index) internally, which throws
            // IndexOutOfBoundsException when a refresh shrinks the snapshot while
            // the grid still resolves keys for the old layout. Guard it.
            try {
                if (index < items.itemCount) {
                    val item = items.peek(index)
                    if (item != null) keyForItem?.invoke(item) ?: item.hashCode().toString() else "placeholder:$index"
                } else {
                    "placeholder:$index"
                }
            } catch (_: IndexOutOfBoundsException) {
                "placeholder:$index"
            }
        }
    }
    PagingScaffold(
        itemCount = items.itemCount,
        refreshing = items.loadState.refresh is LoadState.Loading,
        loadingMore = items.loadState.append is LoadState.Loading || items.loadState.prepend is LoadState.Loading,
        errorText = error?.let { strings.error(it.error.message.orEmpty()) },
        emptyText = strings.nothingHere,
        retryText = strings.retry,
        state = state, columns = columns,
        onRefresh = { items.refresh() }, onRetry = { items.retry() },
        enableRefresh = enableRefresh,
        contentPadding = PaddingValues(bottom = bottomInset),
        modifier = modifier,
        itemKey = resolvedItemKey,
    ) { index -> items[index]?.let { itemContent(it) } }
}
