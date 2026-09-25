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
 * Shared state-owning screen for the videos/clips sort dialog.
 *
 * The caller supplies option values with already-resolved labels (platform
 * strings and visibility decisions stay in the app layer); this composable
 * owns the sort/period/type selection state and renders [SortDialogContent].
 * Languages and tag selection stay in the caller (nested dialogs), so the
 * callbacks report the selected values and the caller computes labels and
 * change detection.
 */
@Composable
fun VideosSortScreen(
    sortTitle: String,
    sortOptions: List<SortOption>,
    initialSort: String,
    typeTitle: String,
    typeOptions: List<SortOption>,
    initialType: String,
    periodTitle: String,
    periodOptions: List<SortOption>,
    initialPeriod: String,
    showSortAndType: Boolean,
    showPeriod: Boolean,
    languagesLabel: String?,
    onLanguagesClick: () -> Unit,
    saveDefaultLabel: String,
    saveSortLabel: String?,
    saved: Boolean,
    deleteLabel: String,
    applyLabel: String,
    onApply: (sort: String, period: String, type: String) -> Unit,
    onSaveDefault: (sort: String, period: String, type: String) -> Unit,
    onSaveSort: (sort: String, period: String, type: String) -> Unit,
    onDeleteSaved: () -> Unit,
    contentPadding: Dp = 8.dp,
) {
    var sort by rememberSaveable(initialSort) { mutableStateOf(initialSort) }
    var period by rememberSaveable(initialPeriod) { mutableStateOf(initialPeriod) }
    var type by rememberSaveable(initialType) { mutableStateOf(initialType) }
    var savedState by remember(saved) { mutableStateOf(saved) }
    SortDialogContent(
        selections = buildList {
            if (showSortAndType) {
                add(SortSelection(sortTitle, sortOptions, sort, { sort = it }))
                add(SortSelection(typeTitle, typeOptions, type, { type = it }))
            }
            if (showPeriod) {
                add(SortSelection(periodTitle, periodOptions, period, { period = it }))
            }
        },
        actions = buildList {
            if (languagesLabel != null) {
                add(SortDialogAction(languagesLabel, onLanguagesClick))
            }
            add(SortDialogAction(saveDefaultLabel, { onSaveDefault(sort, period, type) }))
            if (saveSortLabel != null) {
                add(SortDialogAction(
                    label = saveSortLabel,
                    onClick = { onSaveSort(sort, period, type) },
                    deleteLabel = deleteLabel,
                    onDelete = if (savedState) {
                        {
                            onDeleteSaved()
                            savedState = false
                        }
                    } else null,
                ))
            }
            add(SortDialogAction(applyLabel, { onApply(sort, period, type) }))
        },
        contentPadding = contentPadding,
    )
}
