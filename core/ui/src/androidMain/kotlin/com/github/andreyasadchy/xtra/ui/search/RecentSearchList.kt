package com.github.andreyasadchy.xtra.ui.search

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.andreyasadchy.xtra.ui.collections.RecentSearchCollectionRow

/**
 * The recent-search list every search screen shows while the query is empty. Shared so the four
 * search fragments carry one copy of the row wiring.
 *
 * It takes plain query strings rather than the `RecentSearch` entity, because that model (and its
 * Room DAO) lives in `:core:database`, which this module sits above. The history/delete drawables
 * and the delete label are app resources, so the host passes them in too.
 */
@Composable
fun RecentSearchList(
    queries: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    historyIcon: Painter,
    deleteIcon: Painter,
    deleteLabel: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(queries, key = { it }) { query ->
            RecentSearchCollectionRow(
                query = query,
                historyIcon = historyIcon,
                deleteIcon = deleteIcon,
                deleteLabel = deleteLabel,
                onClick = { onSelect(query) },
                onDelete = { onDelete(query) },
            )
        }
    }
}
