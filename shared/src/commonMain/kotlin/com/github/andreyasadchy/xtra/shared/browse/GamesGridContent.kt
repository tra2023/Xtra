package com.github.andreyasadchy.xtra.shared.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

/**
 * Shared games grid. Takes a plain [List] so it works without
 * `paging-compose`: Android maps `PagingDataAdapter.snapshot().items` into it
 * (adapter keeps owning refresh/retry/load states), desktop passes its list
 * directly. Mirrors the `GamesAdapter` row: box art, name, viewer count, tag
 * chips. Text formatting stays in the caller via [viewersLabel] (Android
 * plurals) so commonMain needs no resources.
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
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        state = gridState,
        modifier = modifier,
    ) {
        items(games, key = { it.id ?: it.name ?: it.hashCode().toString() }) { game ->
            Card(
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { onGameClick(game) },
            ) {
                Column {
                    XtraAsyncImage(
                        model = game.boxArt,
                        contentDescription = game.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f),
                    )
                    Text(
                        text = game.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    )
                    val viewerCount = game.viewerCount
                    if (viewerCount != null && viewersLabel != null) {
                        Text(
                            text = viewersLabel(viewerCount),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    val broadcasterCount = game.broadcasterCount
                    if (showBroadcasters && broadcasterCount != null && broadcastersLabel != null) {
                        Text(
                            text = broadcastersLabel(broadcasterCount),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    val tags = game.tags
                    if (showTags && !tags.isNullOrEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(8.dp),
                        ) {
                            for (tag in tags) {
                                val name = tag.name ?: continue
                                if (tag.id != null) {
                                    AssistChip(
                                        onClick = { onTagClick(tag) },
                                        label = { Text(name) },
                                    )
                                } else {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
