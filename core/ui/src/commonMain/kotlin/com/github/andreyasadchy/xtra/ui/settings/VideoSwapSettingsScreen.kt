package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun VideoSwapSettingsScreen(
    state: VideoSwapSettingsUiState,
    labels: VideoSwapLabels,
    onAdd: (String, String) -> Unit,
    onEdit: (Int, String, String) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    onReorder: (List<Int>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 0.dp,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var orderedItems by remember { mutableStateOf(state.items) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    val currentState by rememberUpdatedState(state)
    val currentReorder by rememberUpdatedState(onReorder)
    val enabled = !state.loading && !state.saving && state.error == null
    LaunchedEffect(state.items, state.error) {
        draggingId = null
        orderedItems = state.items
    }
    val edgeSize = with(LocalDensity.current) { 56.dp.toPx() }

    fun moveDraggedItem() {
        val id = draggingId ?: return
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val dragged = visibleItems.find { it.key == id } ?: return
        val from = orderedItems.indexOfFirst { it.id == id }
        if (from <= 0 || dragged.index != from) return
        val center = dragged.offset + dragged.size / 2f
        val target = if (dragCenter > center) {
            visibleItems.lastOrNull {
                it.key != id && it.offset + it.size / 2f in center..dragCenter &&
                        orderedItems.indexOfFirst { item -> item.id == it.key } > from
            }
        } else {
            visibleItems.firstOrNull {
                it.key != id && it.offset + it.size / 2f in dragCenter..center &&
                        orderedItems.indexOfFirst { item -> item.id == it.key } in 1 until from
            }
        } ?: return
        val to = orderedItems.indexOfFirst { it.id == target.key }
        if (to <= 0) return
        orderedItems = orderedItems.toMutableList().apply { add(to, removeAt(from)) }.toList()
    }

    val moveDrag by rememberUpdatedState(::moveDraggedItem)
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val scroll = when {
                dragCenter < layout.viewportStartOffset + edgeSize ->
                    (dragCenter - layout.viewportStartOffset - edgeSize).coerceAtLeast(-edgeSize) / 5f
                dragCenter > layout.viewportEndOffset - edgeSize ->
                    (dragCenter - layout.viewportEndOffset + edgeSize).coerceAtMost(edgeSize) / 5f
                else -> 0f
            }
            if (scroll != 0f) listState.scrollBy(scroll)
            moveDrag()
        }
    }

    Surface(modifier.fillMaxSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(24.dp))
                Button(onClick = { adding = true }, enabled = enabled && draggingId == null) { Text(labels.add) }
            }
            state.error?.let {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, enabled = !state.loading && !state.saving) { Text(labels.retry) }
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    userScrollEnabled = draggingId == null,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(orderedItems, key = { it.id }) { item ->
                        val dragging = draggingId == item.id
                        val info = listState.layoutInfo.visibleItemsInfo.find { it.key == item.id }
                        val offset = if (dragging && info != null) dragCenter - info.offset - info.size / 2f else 0f
                        Row(
                            modifier = Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = offset }
                                .sizeIn(minHeight = 64.dp).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.isDefault) {
                                Spacer(Modifier.size(48.dp))
                            } else {
                                Box(
                                    modifier = Modifier.size(48.dp).semantics {
                                        contentDescription = "${labels.reorder}: ${item.description}"
                                        customActions = if (enabled && draggingId == null) buildList {
                                            val index = orderedItems.indexOfFirst { it.id == item.id }
                                            if (index > 1) add(CustomAccessibilityAction(labels.moveUp) {
                                                currentReorder(orderedItems.toMutableList().apply { add(index - 1, removeAt(index)) }.drop(1).map { it.id })
                                                true
                                            })
                                            if (index in 1 until orderedItems.lastIndex) add(CustomAccessibilityAction(labels.moveDown) {
                                                currentReorder(orderedItems.toMutableList().apply { add(index + 1, removeAt(index)) }.drop(1).map { it.id })
                                                true
                                            })
                                        } else emptyList()
                                    }.pointerInput(item.id, enabled) {
                                        if (enabled) detectDragGestures(
                                            onDragStart = {
                                                listState.layoutInfo.visibleItemsInfo.find { it.key == item.id }?.let {
                                                    dragCenter = it.offset + it.size / 2f
                                                    draggingId = item.id
                                                }
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragCenter += amount.y
                                                moveDrag()
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                currentReorder(orderedItems.drop(1).map { it.id })
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                orderedItems = currentState.items
                                            },
                                        )
                                    },
                                    contentAlignment = Alignment.Center,
                                ) { VideoSwapIcon(VideoSwapIconType.Drag) }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                Text(if (item.isDefault) labels.defaultValues else item.description)
                                if (item.isDefault) {
                                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (!item.isDefault) {
                                Checkbox(
                                    checked = item.enabled,
                                    onCheckedChange = { onToggle(item.id, it) },
                                    enabled = enabled && draggingId == null,
                                    modifier = Modifier.semantics { contentDescription = "${labels.enabled}: ${item.description}" },
                                )
                            }
                            IconButton(
                                onClick = { editingId = item.id },
                                enabled = enabled && draggingId == null,
                                modifier = Modifier.semantics { contentDescription = "${labels.edit}: ${item.description}" },
                            ) { VideoSwapIcon(VideoSwapIconType.Edit) }
                            if (!item.isDefault) {
                                IconButton(
                                    onClick = { deletingId = item.id },
                                    enabled = enabled && draggingId == null,
                                    modifier = Modifier.semantics { contentDescription = "${labels.delete}: ${item.description}" },
                                ) { VideoSwapIcon(VideoSwapIconType.Delete) }
                            }
                        }
                    }
                }
            }
        }
    }
    val editingItem = state.items.find { it.id == editingId }
    if (adding || editingItem != null) {
        VideoSwapEditDialog(
            item = if (adding) null else editingItem,
            labels = labels,
            onConfirm = { platform, playerType ->
                if (adding) onAdd(platform, playerType) else editingItem?.let { onEdit(it.id, platform, playerType) }
                adding = false
                editingId = null
            },
            onDismiss = {
                adding = false
                editingId = null
            },
        )
    }
    if (state.items.any { it.id == deletingId && !it.isDefault }) {
        VideoSwapDeleteDialog(
            labels = labels,
            onConfirm = {
                deletingId?.let(onDelete)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }
}

private enum class VideoSwapIconType { Drag, Edit, Delete }

@Composable
private fun VideoSwapIcon(type: VideoSwapIconType) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(color, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), stroke)
        }
        when (type) {
            VideoSwapIconType.Drag -> {
                line(.15f, .4f, .85f, .4f)
                line(.15f, .6f, .85f, .6f)
            }
            VideoSwapIconType.Edit -> {
                line(.2f, .65f, .65f, .2f)
                line(.35f, .8f, .8f, .35f)
                line(.65f, .2f, .8f, .35f)
                line(.2f, .65f, .15f, .85f)
                line(.15f, .85f, .35f, .8f)
            }
            VideoSwapIconType.Delete -> {
                line(.2f, .25f, .8f, .25f)
                line(.4f, .1f, .6f, .1f)
                line(.3f, .35f, .3f, .85f)
                line(.3f, .85f, .7f, .85f)
                line(.7f, .85f, .7f, .35f)
            }
        }
    }
}
