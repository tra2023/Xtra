package com.github.andreyasadchy.xtra.ui.sort

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared state-owning screen for the streams sort dialog.
 *
 * The caller supplies option values with already-resolved labels (platform
 * strings stay in the app layer); this composable owns the sort selection
 * state and renders [SortDialogContent]. The tag list and selected languages
 * stay in the caller (nested dialogs mutate them), so the callbacks report
 * the selected sort value and the caller computes tags, labels and change
 * detection.
 */
@Composable
fun StreamsSortScreen(
    sortTitle: String,
    sortOptions: List<SortOption>,
    initialSort: String,
    filtersTitle: String,
    tags: List<String>,
    addTagLabel: String,
    removeTagLabel: String,
    onAddTag: () -> Unit,
    onRemoveTag: (Int) -> Unit,
    languagesLabel: String,
    onLanguagesClick: () -> Unit,
    saveDefaultLabel: String,
    saveSortLabel: String?,
    showSaveSort: Boolean,
    saved: Boolean,
    deleteLabel: String,
    saveFiltersLabel: String,
    applyLabel: String,
    onApply: (sort: String) -> Unit,
    onSaveFilters: (sort: String) -> Unit,
    onSaveSort: (sort: String) -> Unit,
    onSaveDefault: (sort: String) -> Unit,
    onDeleteSaved: () -> Unit,
    contentPadding: Dp = 8.dp,
) {
    var sort by rememberSaveable(initialSort) { mutableStateOf(initialSort) }
    var savedState by remember(saved) { mutableStateOf(saved) }
    SortDialogContent(
        selections = listOf(SortSelection(sortTitle, sortOptions, sort, { sort = it })),
        tags = SortTagSelection(
            title = filtersTitle,
            tags = tags,
            addLabel = addTagLabel,
            removeLabel = removeTagLabel,
            onAdd = onAddTag,
            onRemove = onRemoveTag,
        ),
        actions = buildList {
            add(SortDialogAction(languagesLabel, onLanguagesClick))
            add(SortDialogAction(saveDefaultLabel, { onSaveDefault(sort) }))
            if (showSaveSort) {
                add(SortDialogAction(
                    label = saveSortLabel ?: saveDefaultLabel,
                    onClick = { onSaveSort(sort) },
                    deleteLabel = deleteLabel,
                    onDelete = if (savedState) {
                        {
                            onDeleteSaved()
                            savedState = false
                        }
                    } else null,
                ))
            }
            add(SortDialogAction(saveFiltersLabel, { onSaveFilters(sort) }))
            add(SortDialogAction(applyLabel, { onApply(sort) }))
        },
        contentPadding = contentPadding,
    )
}
