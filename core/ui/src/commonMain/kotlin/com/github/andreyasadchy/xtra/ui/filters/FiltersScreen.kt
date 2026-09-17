package com.github.andreyasadchy.xtra.ui.filters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FilterListItem(
    val id: Int,
    val gameName: String?,
    val tags: String?,
    val languages: String?,
)

@Composable
fun FiltersScreen(
    itemCount: Int,
    itemKey: (Int) -> Any,
    itemAt: (Int) -> FilterListItem?,
    loading: Boolean,
    columns: Int,
    state: LazyGridState,
    emptyText: String,
    optionsText: String,
    deleteText: String,
    onOpen: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    cardMargin: Dp = 8.dp,
    cornerRadius: Dp = 12.dp,
    compactText: Boolean = false,
    material3: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            state = state,
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = itemCount, key = itemKey) { index ->
                val item = itemAt(index)
                if (item == null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp))
                } else {
                    FilterCard(
                        item = item,
                        optionsText = optionsText,
                        deleteText = deleteText,
                        onOpen = { onOpen(item.id) },
                        onDelete = { onDelete(item.id) },
                        cardMargin = cardMargin,
                        cornerRadius = cornerRadius,
                        compactText = compactText,
                        material3 = material3,
                    )
                }
            }
        }
        if (itemCount == 0) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun FilterCard(
    item: FilterListItem,
    optionsText: String,
    deleteText: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    cardMargin: Dp,
    cornerRadius: Dp,
    compactText: Boolean,
    material3: Boolean,
) {
    var menuExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val titleStyle = MaterialTheme.typography.titleMedium.let {
        if (compactText) it.copy(lineHeight = 18.sp, letterSpacing = 0.sp) else it
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.let {
        if (compactText) it.copy(lineHeight = 16.sp, letterSpacing = 0.sp) else it
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(cardMargin),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (material3) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (material3) 1.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onOpen,
                onLongClickLabel = deleteText,
                onLongClick = onDelete,
            ),
        ) {
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                item.gameName?.let {
                    Text(text = it, style = titleStyle)
                }
                item.tags?.let {
                    Text(text = it, style = bodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.languages?.let {
                    Text(text = it, style = bodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.size(24.dp).semantics { contentDescription = optionsText }) {
                        for (position in 1..3) {
                            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(size.width / 2, size.height * position / 4))
                        }
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(deleteText) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
