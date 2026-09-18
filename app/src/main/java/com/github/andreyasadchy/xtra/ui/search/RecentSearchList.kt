package com.github.andreyasadchy.xtra.ui.search

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.ui.collections.RecentSearchCollectionRow

/**
 * The recent-search list every search screen shows while the query is empty.
 * Shared so the four search fragments carry one copy of the row wiring.
 */
@Composable
fun RecentSearchList(
    searches: List<RecentSearch>,
    onSelect: (String) -> Unit,
    onDelete: (RecentSearch) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(searches, key = { it.query }) { search ->
            RecentSearchCollectionRow(
                query = search.query,
                historyIcon = painterResource(R.drawable.baseline_history_black_24),
                deleteIcon = painterResource(R.drawable.baseline_delete_black_24),
                deleteLabel = stringResource(R.string.delete),
                onClick = { onSelect(search.query) },
                onDelete = { onDelete(search) },
            )
        }
    }
}
