package com.github.andreyasadchy.xtra.ui.sort

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SortOption(val value: String, val label: String)

data class SortSelection(
    val title: String,
    val options: List<SortOption>,
    val selected: String,
    val onSelect: (String) -> Unit,
)

data class SortTagSelection(
    val title: String,
    val tags: List<String>,
    val addLabel: String,
    val removeLabel: String,
    val onAdd: () -> Unit,
    val onRemove: (Int) -> Unit,
)

data class SortDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val deleteLabel: String? = null,
    val onDelete: (() -> Unit)? = null,
)

@Composable
fun SortDialogContent(
    selections: List<SortSelection> = emptyList(),
    tags: SortTagSelection? = null,
    actions: List<SortDialogAction>,
    contentPadding: Dp = 8.dp,
) {
    Surface {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)) {
            selections.forEach { selection ->
                Column(Modifier.selectableGroup()) {
                    SortHeading(selection.title)
                    selection.options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                                .selectable(
                                    selected = selection.selected == option.value,
                                    role = Role.RadioButton,
                                    onClick = { selection.onSelect(option.value) },
                                ).padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selection.selected == option.value, onClick = null)
                            Text(option.label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            tags?.let { selection ->
                SortHeading(selection.title)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selection.tags.forEachIndexed { index, name ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(name) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { selection.onRemove(index) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "${selection.removeLabel}: $name"
                                    },
                                ) {
                                    SortCloseIcon()
                                }
                            },
                        )
                    }
                }
                TextButton(onClick = selection.onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text(selection.addLabel)
                }
            }
            actions.forEach { action ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = action.onClick, modifier = Modifier.weight(1f)) {
                        Text(action.label)
                    }
                    action.onDelete?.let { onDelete ->
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.semantics {
                                contentDescription = action.deleteLabel ?: action.label
                            },
                        ) {
                            SortCloseIcon()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 5.dp, bottom = 5.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SortCloseIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val inset = 3.dp.toPx()
        drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 2.dp.toPx())
        drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), 2.dp.toPx())
    }
}
