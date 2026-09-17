package com.github.andreyasadchy.xtra.ui.following.channels

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.sort.SortDialogAction
import com.github.andreyasadchy.xtra.ui.sort.SortDialogContent
import com.github.andreyasadchy.xtra.ui.sort.SortOption
import com.github.andreyasadchy.xtra.ui.sort.SortSelection
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FollowedChannelsSortDialog : BottomSheetDialogFragment() {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, order: String, orderText: CharSequence, changed: Boolean, saveDefault: Boolean)
    }

    companion object {
        const val ORDER_ASC = "asc"
        const val ORDER_DESC = "desc"
        const val SORT_FOLLOWED_AT = "created_at"
        const val SORT_ALPHABETICALLY = "login"
        const val SORT_LAST_BROADCAST = "last_broadcast"

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
        val originalSort = requireArguments().getString(SORT).takeIf { value -> sortOptions.any { it.value == value } } ?: SORT_LAST_BROADCAST
        val originalOrder = requireArguments().getString(ORDER).takeIf { value -> orderOptions.any { it.value == value } } ?: ORDER_DESC
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
                var order by rememberSaveable { mutableStateOf(originalOrder) }
                val applyFilters: (Boolean) -> Unit = { saveDefault ->
                    listener.onChange(
                        sort, sortOptions.first { it.value == sort }.label,
                        order, orderOptions.first { it.value == order }.label,
                        sort != originalSort || order != originalOrder, saveDefault,
                    )
                    dismiss()
                }
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    SortDialogContent(
                        selections = listOf(
                            SortSelection(getString(R.string.sort), sortOptions, sort, { sort = it }),
                            SortSelection(getString(R.string.order), orderOptions, order, { order = it }),
                        ),
                        actions = listOf(
                            SortDialogAction(getString(R.string.save_default), { applyFilters(true) }),
                            SortDialogAction(getString(R.string.apply), { applyFilters(false) }),
                        ),
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
