package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class PlayerMenuSnapshot(
    val quality: String? = null,
    val speed: String? = null,
    val vodGames: Boolean = false,
    val bookmarked: Boolean? = null,
    val subtitlesSelected: Boolean? = null,
)

data class PlayerMenuEntry(
    val key: String,
    val label: String,
    val value: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun PlayerMenuSheetContent(
    entries: List<PlayerMenuEntry>,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(contentPadding),
    ) {
        items(entries, key = { it.key }) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = entry.onClick)
                    .heightIn(min = 48.dp)
                    .padding(12.dp),
            ) {
                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                entry.value?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
