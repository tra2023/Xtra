package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class SleepTimerUiState(
    val hours: String,
    val minutes: String,
    val lockScreen: Boolean,
) {
    val hoursValue: Int get() = hours.toIntOrNull() ?: 0
    val minutesValue: Int get() = minutes.toIntOrNull() ?: 0
    val durationMs: Long get() = hoursValue * 3600_000L + minutesValue * 60_000L
}

@Composable
fun SleepTimerDialogContent(
    state: SleepTimerUiState,
    title: String,
    hoursLabel: String,
    minutesLabel: String,
    lockLabel: String,
    confirmLabel: String,
    cancelLabel: String,
    stopLabel: String?,
    onHoursChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onLockChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DurationInput(state.hours, hoursLabel, 23, onHoursChanged, Modifier.weight(1f))
            DurationInput(state.minutes, minutesLabel, 59, onMinutesChanged, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = state.lockScreen,
                role = Role.Checkbox,
                onValueChange = onLockChanged,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.lockScreen, onCheckedChange = null)
            Text(text = lockLabel, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stopLabel != null) {
                TextButton(onClick = onStop) { Text(stopLabel) }
            }
            TextButton(onClick = onCancel) { Text(cancelLabel) }
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        }
    }
}

@Composable
private fun DurationInput(
    value: String,
    label: String,
    maximum: Int,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                if (text.isEmpty() || (text.length <= 2 && text.all { it.isDigit() } && text.toIntOrNull() in 0..maximum)) {
                    onValueChanged(text)
                }
            },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = (value.toIntOrNull() ?: 0).toFloat().coerceIn(0f, maximum.toFloat()),
            onValueChange = { onValueChanged(it.roundToInt().toString()) },
            valueRange = 0f..maximum.toFloat(),
            steps = (maximum - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
