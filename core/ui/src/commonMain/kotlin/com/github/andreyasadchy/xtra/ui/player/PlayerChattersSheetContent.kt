package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList

@Composable
fun PlayerChattersSheetContent(
    viewerList: ChannelViewerList?,
    loading: Boolean,
    error: String?,
    countLabel: String?,
    groupLabels: List<String>,
    emptyLabel: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
    ) {
        if (loading) {
            item("loading") { CircularProgressIndicator(Modifier.padding(16.dp)) }
        }
        if (error != null) {
            item("error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(retryLabel) }
                }
            }
        }
        if (viewerList != null) {
            if (countLabel != null) {
                item("count") { Text(countLabel, style = MaterialTheme.typography.bodyMedium) }
            }
            val groups = listOf(viewerList.broadcasters, viewerList.moderators, viewerList.vips, viewerList.viewers)
            groups.forEachIndexed { groupIndex, users ->
                if (users.isNotEmpty()) {
                    item("group:$groupIndex") {
                        Text(
                            text = groupLabels[groupIndex],
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    itemsIndexed(users, key = { index, _ -> "user:$groupIndex:$index" }) { _, user ->
                        Text(
                            text = user,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (groups.all { it.isEmpty() }) {
                item("empty") { Text(emptyLabel, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
