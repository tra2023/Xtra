package com.github.andreyasadchy.xtra.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Move-to-app-storage dialog shell: the storage list and selection live here
 * on the shared [StorageSelector], the platform storage listing and
 * preferences stay with the caller.
 */
data class MoveStorageDialogLabels(
    val saveTo: String,
    val noStorage: String,
    val selectDirectory: String,
    val confirm: String,
    val dismiss: String,
)

@Composable
fun MoveStorageDialogContent(
    storageNames: List<String>,
    selectedStorage: Int,
    storageAvailable: Boolean,
    labels: MoveStorageDialogLabels,
    onStorageChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(labels.confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(labels.dismiss) }
        },
        text = {
            StorageSelector(
                title = labels.saveTo,
                noStorageText = labels.noStorage,
                selectDirectoryText = labels.selectDirectory,
                available = storageAvailable,
                locations = emptyList(),
                location = 1,
                storageNames = storageNames,
                selectedStorage = selectedStorage,
                directory = null,
                onLocation = {},
                onStorage = onStorageChange,
                onDirectory = {},
            )
        },
    )
}

/** Delete-download dialog shell with the keep-files checkbox. */
data class DeleteDownloadDialogLabels(
    val title: String,
    val message: String,
    val keepFiles: String,
    val confirm: String,
    val dismiss: String,
)

@Composable
fun DeleteDownloadDialogContent(
    keepFiles: Boolean,
    labels: DeleteDownloadDialogLabels,
    onKeepFilesChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.title) },
        text = {
            Column {
                Text(labels.message)
                DownloadCheckBox(labels.keepFiles, keepFiles, onChecked = onKeepFilesChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(labels.confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(labels.dismiss) }
        },
    )
}
