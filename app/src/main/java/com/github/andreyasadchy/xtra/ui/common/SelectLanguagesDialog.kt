package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
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
import com.github.andreyasadchy.xtra.ui.selection.LanguageOption
import com.github.andreyasadchy.xtra.ui.selection.SelectLanguagesScreen
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.getThemeFlags
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Thin Android shell around the shared [SelectLanguagesScreen]: only resolves
 * the platform language list and forwards the selection to
 * [OnSelectedLanguagesChanged].
 */
class SelectLanguagesDialog : BottomSheetDialogFragment() {

    interface OnSelectedLanguagesChanged {
        fun onChange(languages: Array<String>)
    }

    companion object {
        private const val SELECTED_LANGUAGES = "languages"

        fun newInstance(languages: Array<String>): SelectLanguagesDialog {
            return SelectLanguagesDialog().apply {
                arguments = Bundle().apply {
                    putStringArray(SELECTED_LANGUAGES, languages)
                }
            }
        }
    }

    private lateinit var listener: OnSelectedLanguagesChanged

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnSelectedLanguagesChanged
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val languageEntries = resources.getStringArray(R.array.gqlUserLanguageEntries)
        val languages = resources.getStringArray(R.array.gqlUserLanguageValues).mapIndexed { index, language ->
            LanguageOption(language, languageEntries[index])
        }
        val initialSelected = requireArguments().getStringArray(SELECTED_LANGUAGES)?.toList().orEmpty()
        val (darkTheme, amoled, blue) = requireContext().getThemeFlags()
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).let {
            val value = it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        return ComposeView(requireContext()).apply {
            id = R.id.languageLayout
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    SelectLanguagesScreen(
                        languages = languages,
                        initialSelected = initialSelected,
                        applyLabel = getString(R.string.apply),
                        onApply = {
                            listener.onChange(it.toTypedArray())
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

}
