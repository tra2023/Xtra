package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerVolumeDialogContent(
    volume: Float,
    volumeLabel: String,
    volumeIcon: Painter,
    onVolumeChanged: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMuteClick,
            modifier = Modifier.padding(start = 8.dp).semantics {
                stateDescription = volume.toInt().toString()
            },
        ) {
            Icon(painter = volumeIcon, contentDescription = volumeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            onValueChangeFinished = onVolumeChangeFinished,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel },
        )
        Text(
            text = volume.toInt().toString(),
            modifier = Modifier.padding(end = 10.dp).widthIn(min = 40.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
