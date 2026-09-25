package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Minimal PreferenceScreen-like kit backing every settings screen.
 * Rows deliberately mirror the old XML widgets (Switch/List/SeekBar/EditText/
 * click) so behavior and defaults stay 1:1 with the previous UI.
 */
@Composable
fun SettingsList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier = modifier.fillMaxWidth(), content = content)
}

fun LazyListScope.settingsCategory(title: String) {
    item(key = "header:$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

fun LazyListScope.settingsSwitch(
    key: String,
    title: String,
    summary: String? = null,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    item(key = "switch:$key") {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Switch) { onValueChange(!value) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = value, onCheckedChange = null)
        }
        HorizontalDivider()
    }
}

fun LazyListScope.settingsClick(
    key: String,
    title: String,
    summary: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    item(key = "click:$key") {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                val sub = value ?: summary
                if (sub != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (summary != null && value != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
    }
}

fun LazyListScope.settingsSlider(
    key: String,
    title: String,
    summary: String? = null,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    item(key = "slider:$key") {
        var slider by remember(key, value) { mutableFloatStateOf(value.toFloat()) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (summary != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(slider.toInt().toString(), style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { onValueChange(slider.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
            )
        }
        HorizontalDivider()
    }
}

@Composable
fun SettingsListDialog(
    title: String,
    entries: List<String>,
    values: List<String>,
    selected: String?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember(selected) { mutableStateOf(selected ?: values.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    val value = values.getOrNull(index) ?: return@forEachIndexed
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = pending == value, onClick = { pending = value })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pending == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(entry, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { pending?.let(onConfirm) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

@Composable
fun SettingsTextDialog(
    title: String,
    message: String? = null,
    initial: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

@Composable
fun SettingsConfirmDialog(
    title: String? = null,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/**
 * One-line bindings between a preference key and its row. They read defaults
 * matching the old XML (`android:defaultValue`) and resolve titles, summaries
 * and list labels through [SettingsStrings].
 */
fun LazyListScope.settingsBooleanPref(
    key: String,
    default: Boolean,
    strings: SettingsStrings,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    // State must be read in a composable scope; defer via wrapper item.
    item(key = "bool:$key") {
        val setting = rememberBooleanSetting(key, default)
        SettingsSwitchRow(
            title = strings.title(key),
            summary = strings.summary(key),
            checked = setting.value,
            onCheckedChange = {
                setting.value = it
                onChanged?.invoke(it)
            },
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider()
}

fun LazyListScope.settingsListPref(
    key: String,
    default: String?,
    strings: SettingsStrings,
    onCommitted: ((String) -> Unit)? = null,
) {
    item(key = "list:$key") {
        val setting = rememberStringSetting(key, default)
        var open by remember { mutableStateOf(false) }
        val label = strings.entryLabel(key, setting.value) ?: setting.value
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.title(key), style = MaterialTheme.typography.bodyLarge)
                if (label != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        if (open) {
            SettingsListDialog(
                title = strings.title(key),
                entries = strings.entries(key),
                values = strings.entryValues(key),
                selected = setting.value,
                confirmLabel = strings.ok,
                dismissLabel = strings.cancel,
                onConfirm = {
                    setting.value = it
                    open = false
                    onCommitted?.invoke(it)
                },
                onDismiss = { open = false },
            )
        }
    }
}

fun LazyListScope.settingsTextPref(
    key: String,
    default: String?,
    strings: SettingsStrings,
    message: String? = null,
    onCommitted: ((String) -> Unit)? = null,
) {
    item(key = "text:$key") {
        val setting = rememberStringSetting(key, default)
        var open by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.title(key), style = MaterialTheme.typography.bodyLarge)
                val current = setting.value
                if (!current.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (strings.summary(key) != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(strings.summary(key)!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        if (open) {
            SettingsTextDialog(
                title = strings.title(key),
                message = message ?: strings.summary(key),
                initial = setting.value.orEmpty(),
                confirmLabel = strings.ok,
                dismissLabel = strings.cancel,
                onConfirm = {
                    setting.value = it
                    open = false
                    onCommitted?.invoke(it)
                },
                onDismiss = { open = false },
            )
        }
    }
}

fun LazyListScope.settingsSliderPref(
    key: String,
    default: Int,
    range: IntRange,
    strings: SettingsStrings,
    onCommitted: ((Int) -> Unit)? = null,
) {
    item(key = "sliderpref:$key") {
        val setting = rememberIntSetting(key, default)
        var slider by remember(key, setting.value) { mutableFloatStateOf(setting.value.toFloat()) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.title(key), style = MaterialTheme.typography.bodyLarge)
                    if (strings.summary(key) != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(strings.summary(key)!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(slider.toInt().toString(), style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = {
                    setting.value = slider.toInt()
                    onCommitted?.invoke(slider.toInt())
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
            )
        }
        HorizontalDivider()
    }
}
