package com.github.andreyasadchy.xtra.ui.paging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PagingScaffold(
    itemCount: Int,
    refreshing: Boolean,
    loadingMore: Boolean,
    errorText: String?,
    emptyText: String,
    retryText: String,
    state: LazyGridState,
    columns: Int,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enableRefresh: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemKey: ((Int) -> Any)? = null,
    itemContent: @Composable (Int) -> Unit,
) {
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            // No stretch rubber-band at the list ends: it fights the collapsing
            // header and pull-to-refresh for the same edge gestures and reads
            // as wobble. The list just stops instead.
            SuppressOverscroll {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns.coerceAtLeast(1)),
                    state = state,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(itemCount, key = itemKey) { index ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp)) { itemContent(index) }
                    }
                }
            }
            if (itemCount == 0) {
                Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        refreshing -> CircularProgressIndicator()
                        errorText != null -> {
                            Text(errorText)
                            Button(onClick = onRetry) { Text(retryText) }
                        }
                        else -> Text(emptyText)
                    }
                }
            } else if (loadingMore) {
                CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(16.dp))
            } else if (errorText != null) {
                Button(onClick = onRetry, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(retryText) }
            }
        }
    }
    if (enableRefresh) {
        PullToRefreshBox(isRefreshing = refreshing && itemCount > 0, onRefresh = onRefresh, modifier = modifier) { content() }
    } else {
        Box(modifier) { content() }
    }
}
