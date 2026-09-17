package com.github.andreyasadchy.xtra.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RadioButtonDialogContent(
    labels: List<String>,
    checkedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
) {
    Surface(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(contentPadding).selectableGroup()
        ) {
            labels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                        .selectable(
                            selected = index == checkedIndex,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ).padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = index == checkedIndex, onClick = null)
                    Text(label, Modifier.weight(1f).padding(start = 12.dp))
                }
            }
        }
    }
}
