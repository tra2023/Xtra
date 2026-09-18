package com.github.andreyasadchy.xtra.ui.bookmarks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

/**
 * One bookmark row, pre-formatted by the caller: every date/duration/count label
 * arrives as text, so this module needs no platform formatting or resources.
 */
data class BookmarkListItem(
    val id: Int,
    val title: String?,
    val thumbnail: String?,
    /** Overlay on the bottom end of the thumbnail. */
    val date: String?,
    /** Overlay on the bottom start of the thumbnail. */
    val timeLeft: String?,
    /** Overlay on the top start of the thumbnail. */
    val duration: String?,
    /** Overlay on the top end of the thumbnail. */
    val type: String?,
    val channel: String?,
    val channelImage: String?,
    val roundImage: Boolean,
    val game: String?,
    /** Watch progress in 0..1, shown under the thumbnail. */
    val watched: Float?,
    val actions: List<Pair<Int, String>>,
)

/**
 * Card list of saved bookmarks, mirroring `fragment_videos_list_item.xml`: a
 * 16:9 thumbnail with four overlay labels, watch progress, then the channel
 * picture, title, channel and game rows with an overflow menu. Long-press
 * deletes, matching the old `setOnLongClickListener`.
 */
@Composable
fun BookmarksList(
    itemCount: Int,
    itemKey: (Int) -> Any,
    itemAt: (Int) -> BookmarkListItem?,
    columns: Int,
    state: LazyGridState,
    emptyText: String,
    optionsText: String,
    deleteText: String,
    showEmpty: Boolean,
    onOpen: (Int) -> Unit,
    onChannel: (Int) -> Unit,
    onGame: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAction: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    cardMargin: Dp = 8.dp,
    cornerRadius: Dp = 12.dp,
    material3: Boolean = true,
) {
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            state = state,
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(itemCount, key = itemKey) { index ->
                val item = itemAt(index)
                if (item == null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp))
                } else {
                    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
                    val overlay = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 4f),
                    )
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(cardMargin),
                        shape = RoundedCornerShape(cornerRadius),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (material3) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (material3) 1.dp else 0.dp),
                    ) {
                        Column(
                            Modifier
                                .combinedClickable(onClick = { onOpen(item.id) }, onLongClickLabel = deleteText, onLongClick = { onDelete(item.id) })
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box {
                                XtraAsyncImage(
                                    model = item.thumbnail,
                                    contentDescription = null,
                                    // Bookmark thumbnails are local files; caching them adds nothing.
                                    diskCache = false,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                )
                                Overlay(item.duration, overlay, Modifier.align(Alignment.TopStart).padding(10.dp))
                                Overlay(item.type, overlay, Modifier.align(Alignment.TopEnd).padding(10.dp))
                                Overlay(item.timeLeft, overlay, Modifier.align(Alignment.BottomStart).padding(5.dp))
                                Overlay(item.date, overlay, Modifier.align(Alignment.BottomEnd).padding(5.dp))
                            }
                            item.watched?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                item.channelImage?.let {
                                    XtraAsyncImage(
                                        model = it,
                                        contentDescription = item.channel,
                                        circleCrop = item.roundImage,
                                        diskCache = false,
                                        modifier = Modifier.size(50.dp).clickable { onChannel(item.id) },
                                    )
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    item.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                                    item.channel?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clickable { onChannel(item.id) },
                                        )
                                    }
                                    item.game?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clickable { onGame(item.id) },
                                        )
                                    }
                                }
                                Box {
                                    IconButton(onClick = { expanded = true }) {
                                        val color = MaterialTheme.colorScheme.onSurfaceVariant
                                        Canvas(Modifier.size(24.dp).semantics { contentDescription = optionsText }) {
                                            for (position in 1..3) {
                                                drawCircle(color, 2.dp.toPx(), Offset(size.width / 2, size.height * position / 4))
                                            }
                                        }
                                    }
                                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                        item.actions.forEach { (action, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onAction(item.id, action) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showEmpty && itemCount == 0) {
            Text(emptyText, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Overlay(text: String?, style: TextStyle, modifier: Modifier) {
    if (text != null) {
        Text(text = text, style = style, modifier = modifier)
    }
}
