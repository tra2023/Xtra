package com.github.andreyasadchy.xtra.ui.following.channels

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.FollowedChannelsSort
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
class FollowedChannelsSortDialog : BottomSheetDialogFragment() {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, order: String, orderText: CharSequence, changed: Boolean, saveDefault: Boolean)
    }

    companion object {
        const val ORDER_ASC = FollowedChannelsSort.ORDER_ASC
        const val ORDER_DESC = FollowedChannelsSort.ORDER_DESC
        const val SORT_FOLLOWED_AT = FollowedChannelsSort.SORT_FOLLOWED_AT
        const val SORT_ALPHABETICALLY = FollowedChannelsSort.SORT_ALPHABETICALLY
        const val SORT_LAST_BROADCAST = FollowedChannelsSort.SORT_LAST_BROADCAST

        private const val SORT = "sort"
        private const val ORDER = "order"

        fun newInstance(sort: String?, order: String?): FollowedChannelsSortDialog {
            return FollowedChannelsSortDialog().apply {
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
            SortOption(SORT_FOLLOWED_AT, getString(R.string.time_followed)),
            SortOption(SORT_ALPHABETICALLY, getString(R.string.alphabetically)),
            SortOption(SORT_LAST_BROADCAST, getString(R.string.last_broadcast)),
        )
        val orderOptions = listOf(
            SortOption(ORDER_DESC, getString(R.string.descending)),
            SortOption(ORDER_ASC, getString(R.string.ascending)),
        )
        val originalSort = FollowedChannelsSort.sanitizeSort(requireArguments().getString(SORT))
        val originalOrder = FollowedChannelsSort.sanitizeOrder(requireArguments().getString(ORDER))
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
