package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.BookmarksSort
import com.github.andreyasadchy.xtra.ui.sort.DualSortScreen
import com.github.andreyasadchy.xtra.ui.sort.SortOption
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.getThemeFlags
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Thin Android shell around the shared [DualSortScreen]: only resolves
 * platform strings and forwards the selected values to [OnFilter].
 */
class BookmarksSortDialog : BottomSheetDialogFragment() {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, order: String, orderText: CharSequence, changed: Boolean, saveDefault: Boolean)
    }

    companion object {
        const val ORDER_ASC = BookmarksSort.ORDER_ASC
        const val ORDER_DESC = BookmarksSort.ORDER_DESC
        const val SORT_EXPIRES_AT = BookmarksSort.SORT_EXPIRES_AT
        const val SORT_CREATED_AT = BookmarksSort.SORT_CREATED_AT
        const val SORT_SAVED_AT = BookmarksSort.SORT_SAVED_AT

        private const val SORT = "sort"
        private const val ORDER = "order"

        fun newInstance(sort: String?, order: String?): BookmarksSortDialog {
            return BookmarksSortDialog().apply {
                arguments = Bundle().apply {
                    putString(SORT, sort)
                    putString(ORDER, order)
                }
            }
        }
    }

    private lateinit var listener: OnFilter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val sortOptions = listOf(
            SortOption(SORT_EXPIRES_AT, getString(R.string.deletion_date)),
            SortOption(SORT_CREATED_AT, getString(R.string.creation_date)),
            SortOption(SORT_SAVED_AT, getString(R.string.saved_date)),
        )
        val orderOptions = listOf(
            SortOption(ORDER_DESC, getString(R.string.descending)),
            SortOption(ORDER_ASC, getString(R.string.ascending)),
        )
        val originalSort = BookmarksSort.sanitizeSort(requireArguments().getString(SORT))
        val originalOrder = BookmarksSort.sanitizeOrder(requireArguments().getString(ORDER))
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
                    DualSortScreen(
                        sortTitle = getString(R.string.sort),
                        sortOptions = sortOptions,
                        initialSort = originalSort,
                        orderTitle = getString(R.string.order),
                        orderOptions = orderOptions,
                        initialOrder = originalOrder,
                        saveDefaultLabel = getString(R.string.save_default),
                        applyLabel = getString(R.string.apply),
                        onApply = { sort, order, changed ->
                            listener.onChange(
                                sort, sortOptions.first { it.value == sort }.label,
                                order, orderOptions.first { it.value == order }.label,
                                changed, false,
                            )
                            dismiss()
                        },
                        onSaveDefault = { sort, order ->
                            listener.onChange(
                                sort, sortOptions.first { it.value == sort }.label,
                                order, orderOptions.first { it.value == order }.label,
                                sort != originalSort || order != originalOrder, true,
                            )
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

}
