package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compose replacement for `SettingsActivity.showDragListDialog`.
 * Reorder via up/down buttons (accessible), default via radio, visibility via
 * checkbox. Persists the `key:default:enabled` CSV on confirm.
 */
@Composable
fun TabListDialog(
    prefKey: String,
    defaultValue: String,
    group: String,
    title: String,
    strings: SettingsStrings,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = LocalXtraSettings.current
    var items by remember(prefKey) {
        mutableStateOf(
            parseDragList(settings.getString(prefKey, null), defaultValue) { key ->
                strings.tabLabel(group, key, key)
            }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(items, key = { it.key }) { item ->
                    val index = items.indexOfFirst { it.key == item.key }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        items = items.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    }
                                },
                                enabled = index > 0,
                            ) { Text("↑") }
                            IconButton(
                                onClick = {
                                    if (index in 0 until items.lastIndex) {
                                        items = items.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    }
                                },
                                enabled = index in 0 until items.lastIndex,
                            ) { Text("↓") }
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.text, style = MaterialTheme.typography.bodyLarge)
                        }
                        RadioButton(
                            selected = item.default,
                            onClick = {
                                items = items.map { it.copy(default = it.key == item.key) }
                            },
                        )
                        Checkbox(
                            checked = item.enabled,
                            onCheckedChange = { checked ->
                                items = items.map { if (it.key == item.key) it.copy(enabled = checked) else it }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
                TextButton(onClick = {
                    settings.putString(prefKey, serializeDragList(items))
                    onChanged()
                    onDismiss()
                }) { Text(strings.ok) }
            }
        },
    )
}
