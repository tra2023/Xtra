package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.StreamsSort
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.sort.SortOption
import com.github.andreyasadchy.xtra.ui.sort.StreamsSortScreen
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragment
import com.github.andreyasadchy.xtra.util.getThemeFlags
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Thin Android shell around the shared [StreamsSortScreen]: only resolves
 * platform strings, owns the tag/language selections mutated by nested
 * dialogs and forwards the selected values to [OnFilter].
 */
class StreamsSortDialog : BottomSheetDialogFragment(), SearchTagsDialog.OnTagSelectedListener, SelectLanguagesDialog.OnSelectedLanguagesChanged {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, tags: Array<String>, languages: Array<String>, changed: Boolean, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean)
        fun deleteSavedSort()
    }

    companion object {
        const val SORT_VIEWERS = StreamsSort.SORT_VIEWERS
        const val SORT_VIEWERS_ASC = StreamsSort.SORT_VIEWERS_ASC
        const val RECENT = StreamsSort.RECENT

        private const val SORT = "sort"
        private const val TAGS = "tags"
        private const val LANGUAGES = "languages"
        private const val SAVED = "saved"
        private const val SHOW_SAVE_SORT = "show_save_sort"

        fun newInstance(sort: String?, tags: Array<String>?, languages: Array<String>?, saved: Boolean = false, showSaveSort: Boolean? = null): StreamsSortDialog {
            return StreamsSortDialog().apply {
                arguments = Bundle().apply {
                    putString(SORT, sort)
                    putStringArray(TAGS, tags)
                    putStringArray(LANGUAGES, languages)
                    putBoolean(SAVED, saved)
                    showSaveSort?.let { putBoolean(SHOW_SAVE_SORT, it) }
                }
            }
        }
    }

    private lateinit var listener: OnFilter
    private val selectedTags = mutableStateListOf<String>()
    private var selectedLanguages: Array<String> = emptyArray()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val args = requireArguments()
        val sortOptions = listOf(
            SortOption(SORT_VIEWERS, getString(R.string.viewers_high)),
            SortOption(SORT_VIEWERS_ASC, getString(R.string.viewers_low)),
            SortOption(RECENT, getString(R.string.recent)),
        )
        val originalSort = StreamsSort.sanitizeSort(args.getString(SORT))
        val originalTags = args.getStringArray(TAGS) ?: emptyArray()
        val originalLanguages = args.getStringArray(LANGUAGES) ?: emptyArray()
        selectedTags.clear()
        selectedTags.addAll(originalTags)
        selectedLanguages = originalLanguages
        val showSaveSort = if (args.containsKey(SHOW_SAVE_SORT)) {
            args.getBoolean(SHOW_SAVE_SORT)
        } else when (parentFragment) {
            is TopStreamsFragment -> false
            else -> true
        }
        val (darkTheme, amoled, blue) = requireContext().getThemeFlags()
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).let {
            val value = it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        return ComposeView(requireContext()).apply {
            id = R.id.sort
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    StreamsSortScreen(
                        sortTitle = getString(R.string.sort),
                        sortOptions = sortOptions,
                        initialSort = originalSort,
                        filtersTitle = getString(R.string.filters),
                        tags = selectedTags.toList(),
                        addTagLabel = getString(R.string.add_tag),
                        removeTagLabel = getString(R.string.delete),
                        onAddTag = { SearchTagsDialog.newInstance(false).show(childFragmentManager, null) },
                        onRemoveTag = { selectedTags.removeAt(it) },
                        languagesLabel = getString(R.string.languages),
                        onLanguagesClick = {
                            SelectLanguagesDialog.newInstance(selectedLanguages).show(childFragmentManager, "closeOnPip")
                        },
                        saveDefaultLabel = getString(R.string.save_default),
                        saveSortLabel = getString(R.string.save_sort_game),
                        showSaveSort = showSaveSort,
                        saved = args.getBoolean(SAVED),
                        deleteLabel = getString(R.string.delete),
                        saveFiltersLabel = getString(R.string.save_filters),
                        applyLabel = getString(R.string.apply),
                        onApply = { sort -> applyFilters(sort, saveFilters = false, saveSort = false, saveDefault = false) },
                        onSaveFilters = { sort -> applyFilters(sort, saveFilters = true, saveSort = false, saveDefault = false) },
                        onSaveSort = { sort -> applyFilters(sort, saveFilters = false, saveSort = true, saveDefault = false) },
                        onSaveDefault = { sort -> applyFilters(sort, saveFilters = false, saveSort = false, saveDefault = true) },
                        onDeleteSaved = { listener.deleteSavedSort() },
                        contentPadding = padding,
                    )
                }
            }
        }
    }

    private fun applyFilters(sort: String, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        val args = requireArguments()
        val originalSort = StreamsSort.sanitizeSort(args.getString(SORT))
        val originalTags = args.getStringArray(TAGS) ?: emptyArray()
        val originalLanguages = args.getStringArray(LANGUAGES) ?: emptyArray()
        val tags = selectedTags.toTypedArray().sortedArray()
        listener.onChange(
            sort, sortLabel(sort),
            tags,
            selectedLanguages,
            sort != originalSort || !tags.contentEquals(originalTags) || !selectedLanguages.contentEquals(originalLanguages),
            saveFilters,
            saveSort,
            saveDefault,
        )
        dismiss()
    }

    private fun sortLabel(value: String): String = getString(
        when (value) {
            SORT_VIEWERS_ASC -> R.string.viewers_low
            RECENT -> R.string.recent
            else -> R.string.viewers_high
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onTagSelected(tag: Tag) {
        tag.name?.let { name ->
            if (!selectedTags.contains(name)) {
                selectedTags.add(name)
            }
        }
    }

    override fun onChange(languages: Array<String>) {
        selectedLanguages = languages
    }

}
