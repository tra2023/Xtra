package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.VideosSort
import com.github.andreyasadchy.xtra.ui.sort.SortOption
import com.github.andreyasadchy.xtra.ui.sort.VideosSortScreen
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getThemeFlags
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Thin Android shell around the shared [VideosSortScreen]: only resolves
 * platform strings, decides section visibility and forwards the selected
 * values to [OnFilter].
 */
class VideosSortDialog : BottomSheetDialogFragment(), SelectLanguagesDialog.OnSelectedLanguagesChanged {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean)
        fun deleteSavedSort()
    }

    companion object {
        const val PERIOD_DAY = VideosSort.PERIOD_DAY
        const val PERIOD_WEEK = VideosSort.PERIOD_WEEK
        const val PERIOD_MONTH = VideosSort.PERIOD_MONTH
        const val PERIOD_ALL = VideosSort.PERIOD_ALL
        const val SORT_TIME = VideosSort.SORT_TIME
        const val SORT_VIEWS = VideosSort.SORT_VIEWS
        const val VIDEO_TYPE_ALL = VideosSort.VIDEO_TYPE_ALL
        const val VIDEO_TYPE_ARCHIVE = VideosSort.VIDEO_TYPE_ARCHIVE
        const val VIDEO_TYPE_HIGHLIGHT = VideosSort.VIDEO_TYPE_HIGHLIGHT
        const val VIDEO_TYPE_UPLOAD = VideosSort.VIDEO_TYPE_UPLOAD

        private const val SORT = "sort"
        private const val PERIOD = "period"
        private const val TYPE = "type"
        private const val LANGUAGES = "languages"
        private const val SAVED = "saved"
        private const val TAB = "tab"
        private const val CONTEXT = "context"
        private const val HAS_ID = "has_id"

        /**
         * Explicit host description for Compose pager screens, which have no
         * legacy fragment host to sniff: [tab] is `"videos"` or `"clips"`,
         * [context] is `"game"`, `"channel"` or `"followed"`, [hasId] tells
         * whether a game/channel id is present. Null (default) keeps the legacy
         * fragment sniffing for the remaining View hosts.
         */
        fun newInstance(sort: String? = SORT_TIME, period: String? = PERIOD_WEEK, type: String? = VIDEO_TYPE_ALL, languages: Array<String>? = null, saved: Boolean = false, tab: String? = null, context: String? = null, hasId: Boolean? = null): VideosSortDialog {
            return VideosSortDialog().apply {
                arguments = Bundle().apply {
                    putString(SORT, sort)
                    putString(PERIOD, period)
                    putString(TYPE, type)
                    putStringArray(LANGUAGES, languages)
                    putBoolean(SAVED, saved)
                    tab?.let { putString(TAB, it) }
                    context?.let { putString(CONTEXT, it) }
                    hasId?.let { putBoolean(HAS_ID, it) }
                }
            }
        }
    }

    private lateinit var listener: OnFilter
    private var selectedLanguages: Array<String> = emptyArray()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val args = requireArguments()
        val sortOptions = listOf(
            SortOption(SORT_TIME, getString(R.string.upload_date)),
            SortOption(SORT_VIEWS, getString(R.string.view_count)),
        )
        val periodOptions = listOf(
            SortOption(PERIOD_DAY, getString(R.string.today)),
            SortOption(PERIOD_WEEK, getString(R.string.this_week)),
            SortOption(PERIOD_MONTH, getString(R.string.this_month)),
            SortOption(PERIOD_ALL, getString(R.string.all_time)),
        )
        val typeOptions = listOf(
            SortOption(VIDEO_TYPE_ARCHIVE, getString(R.string.video_type_archive)),
            SortOption(VIDEO_TYPE_HIGHLIGHT, getString(R.string.video_type_highlight)),
            SortOption(VIDEO_TYPE_UPLOAD, getString(R.string.video_type_upload)),
            SortOption(VIDEO_TYPE_ALL, getString(R.string.all)),
        )
        val originalSort = VideosSort.sanitizeSort(args.getString(SORT))
        val originalPeriod = VideosSort.sanitizePeriod(args.getString(PERIOD))
        val originalType = VideosSort.sanitizeType(args.getString(TYPE))
        val originalLanguages = args.getStringArray(LANGUAGES) ?: emptyArray()
        selectedLanguages = originalLanguages
        val explicitTab = args.getString(TAB)
        val explicitContext = args.getString(CONTEXT)
        val isClips = explicitTab == "clips"
        val showSortAndType = !isClips
        val showLanguages = explicitContext == "game"
        val showPeriod = when (explicitTab) {
            "clips" -> true
            else -> when (explicitContext) {
                "game" -> !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
                "channel", "followed" -> false
                else -> true
            }
        }
        val showSaveSort = args.getBoolean(HAS_ID)
        val saveSortLabel = getString(
            if (explicitContext == "channel") R.string.save_sort_channel else R.string.save_sort_game
        )
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
                    VideosSortScreen(
                        sortTitle = getString(R.string.sort),
                        sortOptions = sortOptions,
                        initialSort = originalSort,
                        typeTitle = getString(R.string.type),
                        typeOptions = typeOptions,
                        initialType = originalType,
                        periodTitle = getString(R.string.period),
                        periodOptions = periodOptions,
                        initialPeriod = originalPeriod,
                        showSortAndType = showSortAndType,
                        showPeriod = showPeriod,
                        languagesLabel = getString(R.string.languages).takeIf { showLanguages },
                        onLanguagesClick = {
                            SelectLanguagesDialog.newInstance(selectedLanguages).show(childFragmentManager, "closeOnPip")
                        },
                        saveDefaultLabel = getString(R.string.save_default),
                        saveSortLabel = saveSortLabel.takeIf { showSaveSort },
                        saved = args.getBoolean(SAVED),
                        deleteLabel = getString(R.string.delete),
                        applyLabel = getString(R.string.apply),
                        onApply = { sort, period, type ->
                            applyFilters(sort, period, type, saveSort = false, saveDefault = false)
                        },
                        onSaveDefault = { sort, period, type ->
                            applyFilters(sort, period, type, saveSort = false, saveDefault = true)
                        },
                        onSaveSort = { sort, period, type ->
                            applyFilters(sort, period, type, saveSort = true, saveDefault = false)
                        },
                        onDeleteSaved = { listener.deleteSavedSort() },
                        contentPadding = padding,
                    )
                }
            }
        }
    }

    private fun applyFilters(sort: String, period: String, type: String, saveSort: Boolean, saveDefault: Boolean) {
        val args = requireArguments()
        val originalSort = VideosSort.sanitizeSort(args.getString(SORT))
        val originalPeriod = VideosSort.sanitizePeriod(args.getString(PERIOD))
        val originalType = VideosSort.sanitizeType(args.getString(TYPE))
        val originalLanguages = args.getStringArray(LANGUAGES) ?: emptyArray()
        listener.onChange(
            sort, sortLabel(sort),
            period, periodLabel(period),
            type, typeLabel(type),
            selectedLanguages,
            period != originalPeriod || sort != originalSort || type != originalType || !selectedLanguages.contentEquals(originalLanguages),
            saveSort, saveDefault,
        )
        dismiss()
    }

    private fun sortLabel(value: String): String = getString(
        when (value) {
            SORT_VIEWS -> R.string.view_count
            else -> R.string.upload_date
        }
    )

    private fun periodLabel(value: String): String = getString(
        when (value) {
            PERIOD_DAY -> R.string.today
            PERIOD_WEEK -> R.string.this_week
            PERIOD_MONTH -> R.string.this_month
            else -> R.string.all_time
        }
    )

    private fun typeLabel(value: String): String = getString(
        when (value) {
            VIDEO_TYPE_ARCHIVE -> R.string.video_type_archive
            VIDEO_TYPE_HIGHLIGHT -> R.string.video_type_highlight
            VIDEO_TYPE_UPLOAD -> R.string.video_type_upload
            else -> R.string.all
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onChange(languages: Array<String>) {
        selectedLanguages = languages
    }

}
