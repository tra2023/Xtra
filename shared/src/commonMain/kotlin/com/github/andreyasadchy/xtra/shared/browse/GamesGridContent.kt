package com.github.andreyasadchy.xtra.shared.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

/**
 * Shared games grid. Takes a plain [List] so it works without
 * `paging-compose`: Android maps `PagingDataAdapter.snapshot().items` into it
 * (adapter keeps owning refresh/retry/load states), desktop passes its list
 * directly. Mirrors the `GamesAdapter` row from `fragment_games_list_item.xml`:
 * fixed [columns] (portrait/landscape setting), compact horizontal box art +
 * name/viewer count/broadcaster count, tag text below. Text formatting stays in
 * the caller via [viewersLabel] (Android plurals) so commonMain needs no
 * resources.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesGridContent(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    modifier: Modifier = Modifier,
    onTagClick: (Tag) -> Unit = {},
    showTags: Boolean = true,
    viewersLabel: ((Int) -> String)? = null,
    showBroadcasters: Boolean = false,
    broadcastersLabel: ((Int) -> String)? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    columns: Int = 1,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        state = gridState,
        modifier = modifier,
    ) {
        items(games, key = { it.id ?: it.name ?: it.hashCode().toString() }) { game ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable { onGameClick(game) },
            ) {
                Column {
                    Row(modifier = Modifier.padding(10.dp)) {
                        if (game.boxArt != null) {
                            XtraAsyncImage(
                                model = game.boxArt,
                                contentDescription = game.name,
                                modifier = Modifier.size(width = 49.dp, height = 65.dp),
                            )
                        }
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = game.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val viewerCount = game.viewerCount
                            if (viewerCount != null && viewersLabel != null) {
                                Text(
                                    text = viewersLabel(viewerCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            val broadcasterCount = game.broadcasterCount
                            if (showBroadcasters && broadcasterCount != null && broadcastersLabel != null) {
                                Text(
                                    text = broadcastersLabel(broadcasterCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    val tags = game.tags
                    if (showTags && !tags.isNullOrEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                        ) {
                            for (tag in tags) {
                                val name = tag.name ?: continue
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .then(
                                            if (tag.id != null) {
                                                Modifier.clickable { onTagClick(tag) }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
