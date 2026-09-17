package com.github.andreyasadchy.xtra.ui.downloads

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage

data class DownloadFormState(
    val initialized: Boolean = false,
    val quality: Int = 0,
    val from: String = "",
    val to: String = "",
    val fromError: String? = null,
    val toError: String? = null,
    val location: Int = 0,
    val storage: Int = 0,
    val directory: String? = null,
    val downloadChat: Boolean = false,
    val downloadChatEmotes: Boolean = false,
)

data class DownloadFormLabels(
    val quality: String,
    val time: String,
    val from: String,
    val to: String,
    val saveTo: String,
    val noStorage: String,
    val selectDirectory: String,
    val chat: String,
    val emotes: String,
    val cancel: String,
    val download: String,
)

@Composable
fun DownloadChoice(label: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(choices.getOrNull(selected).orEmpty()) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEachIndexed { index, text ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(index) })
                }
            }
        }
    }
}

@Composable
fun StorageSelector(
    title: String,
    noStorageText: String,
    selectDirectoryText: String,
    available: Boolean,
    locations: List<String>,
    location: Int,
    storageNames: List<String>,
    selectedStorage: Int,
    directory: String?,
    onLocation: (Int) -> Unit,
    onStorage: (Int) -> Unit,
    onDirectory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!available) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(noStorageText, color = MaterialTheme.colorScheme.error)
        } else {
            if (locations.isNotEmpty()) DownloadChoice(title, locations, location, onLocation)
            else Text(title, style = MaterialTheme.typography.titleSmall)
            if (locations.isNotEmpty() && location == 0) {
                directory?.let { Text(it) }
                Button(onClick = onDirectory) { Text(selectDirectoryText) }
            } else {
                storageNames.forEachIndexed { index, name ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selectedStorage == index, role = Role.RadioButton, onClick = { onStorage(index) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedStorage == index, onClick = null)
                        Text(name, Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadForm(
    state: DownloadFormState,
    labels: DownloadFormLabels,
    qualities: List<String>,
    preview: String?,
    previewTitle: String?,
    duration: String?,
    defaultFrom: String,
    defaultTo: String,
    storageAvailable: Boolean,
    locations: List<String>,
    storageNames: List<String>,
    onChange: (DownloadFormState) -> Unit,
    onDirectory: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!state.initialized || qualities.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            if (preview != null) XtraAsyncImage(preview, previewTitle, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            previewTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            DownloadChoice(labels.quality, qualities, state.quality) { onChange(state.copy(quality = it)) }
            if (duration != null) {
                val fromFocus = remember { FocusRequester() }
                val toFocus = remember { FocusRequester() }
                LaunchedEffect(state.fromError, state.toError) {
                    if (state.fromError != null) fromFocus.requestFocus()
                    else if (state.toError != null) toFocus.requestFocus()
                }
                Text(labels.time, style = MaterialTheme.typography.titleSmall)
                Text(duration)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.from,
                        onValueChange = {
                            val value = formatDownloadTime(state.from, it)
                            onChange(state.copy(from = value, fromError = null))
                            if (value.length == 8) toFocus.requestFocus()
                        },
                        label = { Text(labels.from) }, placeholder = { Text(defaultFrom) },
                        isError = state.fromError != null,
                        supportingText = state.fromError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f).focusRequester(fromFocus),
                    )
                    OutlinedTextField(
                        value = state.to,
                        onValueChange = { onChange(state.copy(to = formatDownloadTime(state.to, it), toError = null)) },
                        label = { Text(labels.to) }, placeholder = { Text(defaultTo) },
                        isError = state.toError != null,
                        supportingText = state.toError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f).focusRequester(toFocus),
                    )
                }
            }
            StorageSelector(
                labels.saveTo, labels.noStorage, labels.selectDirectory, storageAvailable,
                locations, state.location, storageNames, state.storage, state.directory,
                { onChange(state.copy(location = it)) }, { onChange(state.copy(storage = it)) }, onDirectory,
            )
            DownloadCheckBox(labels.chat, state.downloadChat, onChecked = { onChange(state.copy(downloadChat = it)) })
            DownloadCheckBox(labels.emotes, state.downloadChatEmotes, state.downloadChat) { onChange(state.copy(downloadChatEmotes = it)) }
        }
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text(labels.cancel) }
            if (storageAvailable && state.initialized && qualities.isNotEmpty()) {
                TextButton(onClick = onDownload, enabled = state.location != 0 || state.directory != null) { Text(labels.download) }
            }
        }
    }
}

private fun formatDownloadTime(old: String, input: String): String {
    var value = input.filter { it.isDigit() || it == ':' || it == '.' }.take(8)
    if (value.endsWith('.')) value = value.dropLast(1) + ":"
    if (value.length > old.length && !old.endsWith(':') && value.takeLast(2).let { it.length == 2 && it.all(Char::isDigit) } && (value.length == 2 || value.getOrNull(value.length - 3) == ':') && value.count { it == ':' } < 2) {
        value += ":"
    }
    return value.take(8)
}

@Composable
fun DownloadCheckBox(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(checked, enabled = enabled, role = Role.Checkbox, onClick = { onChecked(!checked) }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.padding(8.dp), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

data class DownloadListItem(
    val id: Int,
    val title: String?,
    val thumbnail: String?,
    val channel: String?,
    val channelImage: String?,
    val roundImage: Boolean,
    val game: String?,
    val details: List<String>,
    val status: String?,
    val progress: Float?,
    val chatStatus: String?,
    val chatProgress: Float?,
    val watched: Float?,
    val actions: List<Pair<Int, String>>,
)

@Composable
fun DownloadsList(
    itemCount: Int,
    itemKey: (Int) -> Any,
    itemAt: (Int) -> DownloadListItem?,
    loading: Boolean,
    columns: Int,
    state: LazyGridState,
    emptyText: String,
    optionsText: String,
    deleteText: String,
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
        LazyVerticalGrid(GridCells.Fixed(columns.coerceAtLeast(1)), state = state, contentPadding = PaddingValues(bottom = bottomPadding), modifier = Modifier.fillMaxSize()) {
            items(itemCount, key = itemKey) { index ->
                val item = itemAt(index)
                if (item == null) Box(Modifier.fillMaxWidth().heightIn(min = 80.dp))
                else {
                    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(cardMargin), shape = RoundedCornerShape(cornerRadius),
                        colors = CardDefaults.elevatedCardColors(containerColor = if (material3) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (material3) 1.dp else 0.dp),
                    ) {
                        Column(Modifier.combinedClickable(onClick = { onOpen(item.id) }, onLongClickLabel = deleteText, onLongClick = { onDelete(item.id) }).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            XtraAsyncImage(item.thumbnail, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            item.watched?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                item.channelImage?.let { XtraAsyncImage(it, item.channel, Modifier.size(40.dp).clickable { onChannel(item.id) }, circleCrop = item.roundImage) }
                                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    item.channel?.let { Text(it, Modifier.clickable { onChannel(item.id) }, style = MaterialTheme.typography.titleSmall) }
                                    item.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                                }
                                Box {
                                    IconButton(onClick = { expanded = true }) {
                                        val color = MaterialTheme.colorScheme.onSurfaceVariant
                                        Canvas(Modifier.size(24.dp).semantics { contentDescription = optionsText }) {
                                            for (position in 1..3) drawCircle(color, 2.dp.toPx(), Offset(size.width / 2, size.height * position / 4))
                                        }
                                    }
                                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                        item.actions.forEach { (action, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onAction(item.id, action) }) }
                                    }
                                }
                            }
                            item.game?.let { Text(it, Modifier.clickable { onGame(item.id) }, style = MaterialTheme.typography.bodyMedium) }
                            item.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            item.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            item.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                            item.chatStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            item.chatProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        }
        if (itemCount == 0) {
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else Text(emptyText, Modifier.align(Alignment.Center))
        }
    }
}
