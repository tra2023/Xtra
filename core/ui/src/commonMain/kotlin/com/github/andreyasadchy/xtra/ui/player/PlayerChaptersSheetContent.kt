package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

@Composable
fun PlayerChaptersSheetContent(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    positionLabel: (Int) -> String?,
    durationLabel: (Int) -> String?,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    contentPadding: Dp = 0.dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(contentPadding),
    ) {
        items(games) { game ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable(role = Role.Button) { onGameClick(game) },
            ) {
                Row(Modifier.heightIn(min = 48.dp).padding(10.dp)) {
                    game.boxArt?.let {
                        XtraAsyncImage(
                            model = it,
                            contentDescription = game.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(width = 49.dp, height = 65.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = if (game.boxArt != null) 10.dp else 0.dp)) {
                        game.name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        game.vodPosition?.let(positionLabel)?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        game.vodDuration?.let(durationLabel)?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
