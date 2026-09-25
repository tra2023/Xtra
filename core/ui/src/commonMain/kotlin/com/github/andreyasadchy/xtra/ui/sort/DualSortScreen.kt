package com.github.andreyasadchy.xtra.ui.sort

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared state-owning screen for the two-level (sort + order) sort dialogs
 * such as bookmarks and followed channels.
 *
 * The caller supplies option values with already-resolved labels (platform
 * strings stay in the app layer); this composable owns the selection state,
 * renders [SortDialogContent] and reports value changes. Label lookup for the
 * reported values is the caller's job.
 */
@Composable
fun DualSortScreen(
    sortTitle: String,
    sortOptions: List<SortOption>,
    initialSort: String,
    orderTitle: String,
    orderOptions: List<SortOption>,
    initialOrder: String,
    saveDefaultLabel: String,
    applyLabel: String,
    onApply: (sortValue: String, orderValue: String, changed: Boolean) -> Unit,
    onSaveDefault: (sortValue: String, orderValue: String) -> Unit,
    contentPadding: Dp = 8.dp,
) {
    var sort by rememberSaveable(initialSort) { mutableStateOf(initialSort) }
    var order by rememberSaveable(initialOrder) { mutableStateOf(initialOrder) }
    SortDialogContent(
        selections = listOf(
            SortSelection(sortTitle, sortOptions, sort, { sort = it }),
            SortSelection(orderTitle, orderOptions, order, { order = it }),
        ),
        actions = listOf(
            SortDialogAction(saveDefaultLabel, { onSaveDefault(sort, order) }),
            SortDialogAction(applyLabel, { onApply(sort, order, sort != initialSort || order != initialOrder) }),
        ),
        contentPadding = contentPadding,
    )
}
