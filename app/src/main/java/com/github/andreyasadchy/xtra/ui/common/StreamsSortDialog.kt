package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsFragment
import com.github.andreyasadchy.xtra.ui.sort.SortDialogAction
import com.github.andreyasadchy.xtra.ui.sort.SortDialogContent
import com.github.andreyasadchy.xtra.ui.sort.SortOption
import com.github.andreyasadchy.xtra.ui.sort.SortSelection
import com.github.andreyasadchy.xtra.ui.sort.SortTagSelection
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class StreamsSortDialog : BottomSheetDialogFragment(), SearchTagsDialog.OnTagSelectedListener, SelectLanguagesDialog.OnSelectedLanguagesChanged {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, tags: Array<String>, languages: Array<String>, changed: Boolean, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean)
        fun deleteSavedSort()
    }

    companion object {
        const val SORT_VIEWERS = "VIEWER_COUNT"
        const val SORT_VIEWERS_ASC = "VIEWER_COUNT_ASC"
        const val RECENT = "RECENT"

        private const val SORT = "sort"
        private const val TAGS = "tags"
        private const val LANGUAGES = "languages"
        private const val SAVED = "saved"

        fun newInstance(sort: String?, tags: Array<String>?, languages: Array<String>?, saved: Boolean = false): StreamsSortDialog {
            return StreamsSortDialog().apply {
                arguments = Bundle().apply {
                    putString(SORT, sort)
                    putStringArray(TAGS, tags)
                    putStringArray(LANGUAGES, languages)
                    putBoolean(SAVED, saved)
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
        val originalSort = args.getString(SORT).takeIf { value -> sortOptions.any { it.value == value } } ?: SORT_VIEWERS
        val originalTags = args.getStringArray(TAGS) ?: emptyArray()
        val originalLanguages = args.getStringArray(LANGUAGES) ?: emptyArray()
        selectedTags.clear()
        selectedTags.addAll(originalTags)
        selectedLanguages = originalLanguages
        val showSaveSort = when (parentFragment) {
            is GameStreamsFragment -> !parentFragment?.arguments?.getString(C.GAME_ID).isNullOrBlank()
            is TopStreamsFragment -> false
            else -> true
        }
        val (darkTheme, amoled, blue) = themeFlags()
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).let {
            val value = it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        return ComposeView(requireContext()).apply {
            id = R.id.sort
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var sort by rememberSaveable { mutableStateOf(originalSort) }
                var saved by remember { mutableStateOf(args.getBoolean(SAVED)) }
                val applyFilters: (Boolean, Boolean, Boolean) -> Unit = { saveFilters, saveSort, saveDefault ->
                    val tags = selectedTags.toTypedArray().sortedArray()
                    listener.onChange(
                        sort,
                        sortOptions.first { it.value == sort }.label,
                        tags,
                        selectedLanguages,
                        sort != originalSort || !tags.contentEquals(originalTags) || !selectedLanguages.contentEquals(originalLanguages),
                        saveFilters,
                        saveSort,
                        saveDefault,
                    )
                    dismiss()
                }
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    SortDialogContent(
                        selections = listOf(SortSelection(getString(R.string.sort), sortOptions, sort, { sort = it })),
                        tags = SortTagSelection(
                            title = getString(R.string.filters),
                            tags = selectedTags.toList(),
                            addLabel = getString(R.string.add_tag),
                            removeLabel = getString(R.string.delete),
                            onAdd = { SearchTagsDialog.newInstance(false).show(childFragmentManager, null) },
                            onRemove = { selectedTags.removeAt(it) },
                        ),
                        actions = buildList {
                            add(SortDialogAction(getString(R.string.languages), {
                                SelectLanguagesDialog.newInstance(selectedLanguages).show(childFragmentManager, "closeOnPip")
                            }))
                            add(SortDialogAction(getString(R.string.save_default), { applyFilters(false, false, true) }))
                            if (showSaveSort) {
                                add(SortDialogAction(
                                    label = getString(R.string.save_sort_game),
                                    onClick = { applyFilters(false, true, false) },
                                    deleteLabel = getString(R.string.delete),
                                    onDelete = if (saved) {
                                        {
                                            listener.deleteSavedSort()
                                            saved = false
                                        }
                                    } else null,
                                ))
                            }
                            add(SortDialogAction(getString(R.string.save_filters), { applyFilters(true, false, false) }))
                            add(SortDialogAction(getString(R.string.apply), { applyFilters(false, false, false) }))
                        },
                        contentPadding = padding,
                    )
                }
            }
        }
    }

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

    private fun themeFlags(): Triple<Boolean, Boolean, Boolean> {
        val prefs = requireContext().prefs()
        val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> prefs.getString(C.UI_THEME_DARK_ON, "0") ?: "0"
                else -> prefs.getString(C.UI_THEME_DARK_OFF, "2") ?: "2"
            }
        } else {
            prefs.getString(C.THEME, "0") ?: "0"
        }
        return Triple(theme != "2" && theme != "5", theme == "1" || theme == "6", theme == "3")
    }
}
