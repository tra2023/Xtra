package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * Shared sort row for the Compose screens, mirroring `sort_bar.xml`: the sort
 * and filter texts on the left, sort affordance on the right. The icon is
 * supplied by the caller so the shared module needs no platform resources.
 */
@Composable
fun SortRow(
    sortText: CharSequence?,
    filtersText: CharSequence?,
    sortIcon: Painter,
    onClick: () -> Unit,
) {
    val strings = LocalXtraStrings.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            if (sortText != null) {
                Text(text = sortText.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            if (filtersText != null) {
                Text(text = filtersText.toString(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Icon(sortIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = strings.sort,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
