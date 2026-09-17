package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.selection.LanguageOption
import com.github.andreyasadchy.xtra.ui.selection.SelectLanguagesDialogContent
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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
    private val selectedLanguages = mutableStateListOf<String>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnSelectedLanguagesChanged
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguages.clear()
        selectedLanguages.addAll(
            savedInstanceState?.getStringArray(SELECTED_LANGUAGES)
                ?: requireArguments().getStringArray(SELECTED_LANGUAGES)
                ?: emptyArray()
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val languageEntries = resources.getStringArray(R.array.gqlUserLanguageEntries)
        val languages = resources.getStringArray(R.array.gqlUserLanguageValues).mapIndexed { index, language ->
            LanguageOption(language, languageEntries[index])
        }
        val applyLabel = getString(R.string.apply)
        val (darkTheme, amoled, blue) = themeFlags()
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
                    SelectLanguagesDialogContent(
                        languages = languages,
                        selectedLanguages = selectedLanguages.toList(),
                        applyLabel = applyLabel,
                        onToggle = { language, checked ->
                            if (checked) {
                                selectedLanguages.add(language)
                            } else {
                                selectedLanguages.remove(language)
                            }
                        },
                        onApply = {
                            listener.onChange(selectedLanguages.toTypedArray().sortedArray())
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArray(SELECTED_LANGUAGES, selectedLanguages.toTypedArray())
        super.onSaveInstanceState(outState)
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
