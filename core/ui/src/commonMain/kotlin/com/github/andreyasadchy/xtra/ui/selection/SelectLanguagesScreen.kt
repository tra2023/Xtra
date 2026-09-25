package com.github.andreyasadchy.xtra.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared state-owning screen for the select-languages dialog.
 *
 * The caller supplies the language options with already-resolved labels
 * (platform resources stay in the app layer); this composable owns the
 * selection state and renders [SelectLanguagesDialogContent].
 */
@Composable
fun SelectLanguagesScreen(
    languages: List<LanguageOption>,
    initialSelected: List<String>,
    applyLabel: String,
    onApply: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
) {
    var selected by rememberSaveable(initialSelected) { mutableStateOf(initialSelected) }
    SelectLanguagesDialogContent(
        languages = languages,
        selectedLanguages = selected,
        applyLabel = applyLabel,
        onToggle = { language, checked ->
            selected = if (checked) {
                if (language in selected) selected else selected + language
            } else {
                selected - language
            }
        },
        onApply = { onApply(selected.sorted()) },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
