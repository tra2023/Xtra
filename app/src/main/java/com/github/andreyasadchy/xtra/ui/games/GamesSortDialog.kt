package com.github.andreyasadchy.xtra.ui.games

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
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.common.SearchTagsDialog
import com.github.andreyasadchy.xtra.ui.sort.TagSortScreen
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.getThemeFlags
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Thin Android shell around the shared [TagSortScreen]: only owns the tag
 * selection mutated by the nested search dialog and forwards it to [OnFilter].
 */
class GamesSortDialog : BottomSheetDialogFragment(), SearchTagsDialog.OnTagSelectedListener {

    interface OnFilter {
        fun onChange(tags: Array<Tag>)
    }

    companion object {
        private const val TAG_IDS = "tag_ids"
        private const val TAG_NAMES = "tag_names"

        fun newInstance(tagIds: Array<String>?, tagNames: Array<String>?): GamesSortDialog {
            return GamesSortDialog().apply {
                arguments = Bundle().apply {
                    putStringArray(TAG_IDS, tagIds)
                    putStringArray(TAG_NAMES, tagNames)
                }
            }
        }
    }

    private lateinit var listener: OnFilter
    private val selectedTags = mutableStateListOf<Tag>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val args = requireArguments()
        val originalTagIds = args.getStringArray(TAG_IDS) ?: emptyArray()
        val originalTags = args.getStringArray(TAG_NAMES)?.let { names ->
            originalTagIds.zip(names).map { Tag(id = it.first, name = it.second) }
        } ?: emptyList()
        selectedTags.clear()
        val restoredTagIds = savedInstanceState?.getStringArray(TAG_IDS)
        val restoredTagNames = savedInstanceState?.getStringArray(TAG_NAMES)
        selectedTags.addAll(
            if (restoredTagIds != null && restoredTagNames != null) {
                restoredTagIds.zip(restoredTagNames).map { Tag(id = it.first, name = it.second) }
            } else {
                originalTags
            }
        )
        val (darkTheme, amoled, blue) = requireContext().getThemeFlags()
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).let {
            val value = it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    TagSortScreen(
                        filtersTitle = getString(R.string.filters),
                        tags = selectedTags.map { it.name.orEmpty() },
                        addTagLabel = getString(R.string.add_tag),
                        removeTagLabel = getString(R.string.delete),
                        onAddTag = { SearchTagsDialog.newInstance(true).show(childFragmentManager, null) },
                        onRemoveTag = { selectedTags.removeAt(it) },
                        applyLabel = getString(R.string.apply),
                        onApply = {
                            val tags = selectedTags.sortedBy { it.id }
                            if (!tags.mapNotNull { it.id }.toTypedArray().contentEquals(originalTagIds)) {
                                listener.onChange(tags.toTypedArray())
                            }
                            dismiss()
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArray(TAG_IDS, selectedTags.mapNotNull { it.id }.toTypedArray())
        outState.putStringArray(TAG_NAMES, selectedTags.map { it.name.orEmpty() }.toTypedArray())
        super.onSaveInstanceState(outState)
    }

    override fun onTagSelected(tag: Tag) {
        if (tag.id != null && selectedTags.none { it.id == tag.id }) {
            selectedTags.add(tag)
        }
    }

}
