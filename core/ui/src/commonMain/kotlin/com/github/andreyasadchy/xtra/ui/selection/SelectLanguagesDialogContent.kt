package com.github.andreyasadchy.xtra.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class LanguageOption(val value: String, val label: String)

@Composable
fun SelectLanguagesDialogContent(
    languages: List<LanguageOption>,
    selectedLanguages: List<String>,
    applyLabel: String,
    onToggle: (String, Boolean) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
) {
    Surface(modifier) {
        Column(Modifier.fillMaxWidth().padding(contentPadding)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                languages.forEach { language ->
                    val checked = language.value in selectedLanguages
                    Row(
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(language.value, it) },
                            ).padding(horizontal = 5.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(language.label, Modifier.weight(1f).padding(start = 12.dp))
                    }
                }
            }
            TextButton(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            ) {
                Text(applyLabel)
            }
        }
    }
}
