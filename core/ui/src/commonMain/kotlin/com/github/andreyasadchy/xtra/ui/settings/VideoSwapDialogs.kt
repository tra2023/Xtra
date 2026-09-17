package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun VideoSwapEditDialog(
    item: VideoSwapListItem?,
    labels: VideoSwapLabels,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var platform by rememberSaveable(item?.id) { mutableStateOf(item?.platform.orEmpty()) }
    var playerType by rememberSaveable(item?.id) { mutableStateOf(item?.playerType.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val confirm = { onConfirm(platform, playerType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) labels.add else labels.edit) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = platform,
                    onValueChange = { platform = it },
                    label = { Text(labels.platform) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = playerType,
                    onValueChange = { playerType = it },
                    label = { Text(labels.playerType) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = confirm) { Text(labels.confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } },
    )
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

@Composable
fun VideoSwapDeleteDialog(
    labels: VideoSwapLabels,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.delete) },
        text = { Text(labels.deleteMessage) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(labels.delete) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } },
    )
}
