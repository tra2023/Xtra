package com.github.andreyasadchy.xtra.ui.paging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow

/**
 * Snapshot view over a [PagingData] flow for the saved card lists
 * (`DownloadsList`, `FiltersScreen`), which render snapshot grids instead of
 * the shared [com.github.andreyasadchy.xtra.ui.common.PagingGrid].
 *
 * Replaces the `AsyncPagingDataDiffer` + `pageVersion` bridges the app
 * fragments used to feed those lists: `peek` never triggers loads (for keys
 * and lookups), `get` resolves placeholders and triggers loads (for rows),
 * and [loading] mirrors the refresh load state the differs mapped manually.
 */
class PagingSnapshot<T : Any>(private val items: LazyPagingItems<T>) {
    val itemCount: Int get() = items.itemCount

    fun peek(index: Int): T? = items.peek(index)

    operator fun get(index: Int): T? = items[index]

    val loading: Boolean get() = items.loadState.refresh is LoadState.Loading
}

@Composable
fun <T : Any> rememberPagingSnapshot(flow: Flow<PagingData<T>>): PagingSnapshot<T> {
    val items = flow.collectAsLazyPagingItems()
    return PagingSnapshot(items)
}

/** Finds a loaded item by id without triggering loads, for click handlers. */
fun <T : Any> PagingSnapshot<T>.findById(id: Any?, idOf: (T) -> Any?): T? {
    if (id == null) return null
    return (0 until itemCount).mapNotNull { peek(it) }.find { idOf(it) == id }
}

/**
 * Scroll-to-top request counter for snapshot grids, shared by the downloads
 * and filters lists: counts how often a *new* first item appears while the
 * list grows (a newly saved download/filter inserted at position 0), so hosts
 * can animate to it. Mirrors the old `AsyncPagingDataDiffer` insert callback
 * and the bookmarks list's `firstId` tracking.
 */
@Composable
fun <T : Any> PagingSnapshot<T>.rememberInsertionScroll(idOf: (T) -> Any?): Int {
    var scrollRequests by remember { mutableIntStateOf(0) }
    var firstId by remember { mutableStateOf<Any?>(null) }
    var received by remember { mutableStateOf(false) }
    var lastCount by remember { mutableIntStateOf(0) }
    val firstNow = if (itemCount > 0) peek(0)?.let(idOf) else null
    LaunchedEffect(firstNow) {
        if (received && firstNow != null && firstNow != firstId && itemCount > lastCount) scrollRequests++
        lastCount = itemCount
        if (itemCount > 0) received = true
        firstId = firstNow
    }
    return scrollRequests
}
