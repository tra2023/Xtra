package com.github.andreyasadchy.xtra.ui.sort

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared stateless screen for the tag-only sort dialog (games).
 *
 * The tag list is owned by the caller (a nested dialog mutates it), so this
 * composable only renders [SortDialogContent] and forwards actions.
 */
@Composable
fun TagSortScreen(
    filtersTitle: String,
    tags: List<String>,
    addTagLabel: String,
    removeTagLabel: String,
    onAddTag: () -> Unit,
    onRemoveTag: (Int) -> Unit,
    applyLabel: String,
    onApply: () -> Unit,
    contentPadding: Dp = 8.dp,
) {
    SortDialogContent(
        tags = SortTagSelection(
            title = filtersTitle,
            tags = tags,
            addLabel = addTagLabel,
            removeLabel = removeTagLabel,
            onAdd = onAddTag,
            onRemove = onRemoveTag,
        ),
        actions = listOf(
            SortDialogAction(applyLabel, onApply),
        ),
        contentPadding = contentPadding,
    )
}
