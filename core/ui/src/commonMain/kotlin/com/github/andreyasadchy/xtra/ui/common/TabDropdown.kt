package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * Dropdown tab chooser for hosts whose tabs are disabled in settings. The lists
 * themselves stay in the pager; only [onSelect] changes the page.
 */
@Composable
fun TabDropdown(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titles.getOrNull(selectedIndex) ?: titles.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(12.dp)) {
                val caret = Path().apply {
                    moveTo(0f, size.height * 0.3f)
                    lineTo(size.width, size.height * 0.3f)
                    lineTo(size.width / 2f, size.height * 0.8f)
                    close()
                }
                drawPath(caret, color)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            titles.forEachIndexed { index, title ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}
