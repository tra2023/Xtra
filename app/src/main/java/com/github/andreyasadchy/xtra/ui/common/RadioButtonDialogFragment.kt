package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.selection.RadioButtonDialogContent
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class RadioButtonDialogFragment : BottomSheetDialogFragment() {

    interface OnSortOptionChanged {
        fun onChange(requestCode: Int, index: Int, text: CharSequence, tag: String?, tag2: String?)
    }

    companion object {

        private const val REQUEST_CODE = "requestCode"
        private const val LABELS = "labels"
        private const val TAGS = "tags"
        private const val TAGS2 = "tags2"
        private const val CHECKED = "checked"

        fun newInstance(requestCode: Int, labels: Collection<CharSequence>, tags: Array<String>? = null, tags2: Array<String>? = null, checkedIndex: Int): RadioButtonDialogFragment {
            return RadioButtonDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(REQUEST_CODE, requestCode)
                    putCharSequenceArrayList(LABELS, ArrayList(labels))
                    putStringArray(TAGS, tags)
                    putStringArray(TAGS2, tags2)
                    putInt(CHECKED, checkedIndex)
                }
            }
        }
    }

    private lateinit var listenerSort: OnSortOptionChanged

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listenerSort = parentFragment as OnSortOptionChanged
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val args = requireArguments()
        val labels = args.getCharSequenceArrayList(LABELS).orEmpty()
        val displayLabels = labels.map { it.toString() }
        val checkedIndex = args.getInt(CHECKED)
        val requestCode = args.getInt(REQUEST_CODE)
        val tags = args.getStringArray(TAGS)
        val tags2 = args.getStringArray(TAGS2)
        val (darkTheme, amoled, blue) = themeFlags()
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogLayoutPadding)).let {
            val value = it.getDimension(0, 0f) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        return ComposeView(requireContext()).apply {
            id = R.id.sort
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    RadioButtonDialogContent(
                        labels = displayLabels,
                        checkedIndex = checkedIndex,
                        onSelect = { index ->
                            if (index != checkedIndex) {
                                listenerSort.onChange(
                                    requestCode,
                                    index,
                                    labels[index],
                                    tags?.getOrNull(index)?.takeIf { it != "null" },
                                    tags2?.getOrNull(index)?.takeIf { it != "null" },
                                )
                            }
                            dismiss()
                        },
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
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