package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.channel.clips.ChannelClipsFragment
import com.github.andreyasadchy.xtra.ui.channel.videos.ChannelVideosFragment
import com.github.andreyasadchy.xtra.ui.sort.SortDialogAction
import com.github.andreyasadchy.xtra.ui.sort.SortDialogContent
import com.github.andreyasadchy.xtra.ui.sort.SortOption
import com.github.andreyasadchy.xtra.ui.sort.SortSelection
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getThemeFlags
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VideosSortDialog : BottomSheetDialogFragment(), SelectLanguagesDialog.OnSelectedLanguagesChanged {

    interface OnFilter {
        fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean)
        fun deleteSavedSort()
    }

    companion object {
        const val PERIOD_DAY = "day"
        const val PERIOD_WEEK = "week"
        const val PERIOD_MONTH = "month"
        const val PERIOD_ALL = "all"
        const val SORT_TIME = "time"
        const val SORT_VIEWS = "views"
        const val VIDEO_TYPE_ALL = "all"
        const val VIDEO_TYPE_ARCHIVE = "archive"
        const val VIDEO_TYPE_HIGHLIGHT = "highlight"
        const val VIDEO_TYPE_UPLOAD = "upload"

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
        val originalSort = args.getString(SORT).takeIf { value -> sortOptions.any { it.value == value } } ?: SORT_TIME
        val originalPeriod = args.getString(PERIOD).takeIf { value -> periodOptions.any { it.value == value } } ?: PERIOD_WEEK
        val originalType = args.getString(TYPE).takeIf { value -> typeOptions.any { it.value == value } } ?: VIDEO_TYPE_ALL
        val originalLanguages = args.getStringArray(LANGUAGES) ?: emptyArray()
        selectedLanguages = originalLanguages
        val owner = parentFragment
        val explicitTab = args.getString(TAB)
        val explicitContext = args.getString(CONTEXT)
        val isClips = explicitTab?.let { it == "clips" } ?: (owner is ChannelClipsFragment)
        val showSortAndType = !isClips
        val showLanguages = explicitContext?.let { it == "game" }
            ?: (owner !is ChannelClipsFragment && owner !is ChannelVideosFragment)
        val showPeriod = when {
            explicitTab != null -> when {
                explicitTab == "clips" -> true
                explicitContext == "game" -> !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
                explicitContext == "channel" || explicitContext == "followed" -> false
                else -> true
            }
            owner is ChannelVideosFragment -> false
            else -> true
        }
        val showSaveSort = if (args.containsKey(HAS_ID)) {
            args.getBoolean(HAS_ID)
        } else when (owner) {
            is ChannelClipsFragment, is ChannelVideosFragment -> !owner.arguments?.getString(C.CHANNEL_ID).isNullOrBlank()
            else -> true
        }
        val saveSortLabel = getString(
            if (explicitContext?.let { it == "channel" } ?: (owner is ChannelClipsFragment || owner is ChannelVideosFragment)) R.string.save_sort_channel else R.string.save_sort_game
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
                var sort by rememberSaveable { mutableStateOf(originalSort) }
                var period by rememberSaveable { mutableStateOf(originalPeriod) }
                var type by rememberSaveable { mutableStateOf(originalType) }
                var saved by remember { mutableStateOf(args.getBoolean(SAVED)) }
                val applyFilters: (Boolean, Boolean) -> Unit = { saveSort, saveDefault ->
                    listener.onChange(
                        sort, sortOptions.first { it.value == sort }.label,
                        period, periodOptions.first { it.value == period }.label,
                        type, typeOptions.first { it.value == type }.label,
                        selectedLanguages,
                        period != originalPeriod || sort != originalSort || type != originalType || !selectedLanguages.contentEquals(originalLanguages),
                        saveSort, saveDefault,
                    )
                    dismiss()
                }
                XtraTheme(darkTheme = darkTheme, amoled = amoled, blue = blue) {
                    SortDialogContent(
                        selections = buildList {
                            if (showSortAndType) {
                                add(SortSelection(getString(R.string.sort), sortOptions, sort, { sort = it }))
                                add(SortSelection(getString(R.string.type), typeOptions, type, { type = it }))
                            }
                            if (showPeriod) {
                                add(SortSelection(getString(R.string.period), periodOptions, period, { period = it }))
                            }
                        },
                        actions = buildList {
                            if (showLanguages) {
                                add(SortDialogAction(getString(R.string.languages), {
                                    SelectLanguagesDialog.newInstance(selectedLanguages).show(childFragmentManager, "closeOnPip")
                                }))
                            }
                            add(SortDialogAction(getString(R.string.save_default), { applyFilters(false, true) }))
                            if (showSaveSort) {
                                add(SortDialogAction(
                                    label = saveSortLabel,
                                    onClick = { applyFilters(true, false) },
                                    deleteLabel = getString(R.string.delete),
                                    onDelete = if (saved) {
                                        {
                                            listener.deleteSavedSort()
                                            saved = false
                                        }
                                    } else null,
                                ))
                            }
                            add(SortDialogAction(getString(R.string.apply), { applyFilters(false, false) }))
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

    override fun onChange(languages: Array<String>) {
        selectedLanguages = languages
    }

}
