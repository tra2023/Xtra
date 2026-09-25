package com.github.andreyasadchy.xtra.ui.paging

import androidx.compose.runtime.Composable
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
