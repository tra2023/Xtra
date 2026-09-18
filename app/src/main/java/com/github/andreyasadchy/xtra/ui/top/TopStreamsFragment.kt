package com.github.andreyasadchy.xtra.ui.top

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.SortRow
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.RECENT
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.SORT_VIEWERS
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.SORT_VIEWERS_ASC
import com.github.andreyasadchy.xtra.ui.common.StreamsTab
import com.github.andreyasadchy.xtra.ui.common.XtraTopBar
import com.github.andreyasadchy.xtra.ui.common.streamsCompact
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsViewModel.Companion.TopStreamsViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Top streams as a full-Compose screen: app bar, sort row and the shared streams
 * list from `:core:ui`.
 */
class TopStreamsFragment : PagedListFragment(), Scrollable, StreamsSortDialog.OnFilter {

    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: TopStreamsViewModel by viewModels { TopStreamsViewModelFactory }
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    TopStreamsScreen()
                }
            }
        }
    }

    @Composable
    private fun TopStreamsScreen() {
        val activity = requireActivity() as MainActivity
        val isLoggedIn = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        val liftOptOut = !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        val bottomInset = xtraBottomInset(activity)
        val enableScrollTop = !args.tags.isNullOrEmpty() || !args.languages.isNullOrEmpty()
        val refreshTick by composeRefreshSignal.collectAsState()
        val scrollTick by composeScrollTopSignal.collectAsState()
        val sortText by viewModel.sortText.collectAsState()
        val filtersText by viewModel.filtersText.collectAsState()
        Scaffold(
            topBar = {
                Column {
                    XtraTopBar(
                        title = stringResource(R.string.popular),
                        isLoggedIn = isLoggedIn,
                        liftOptOut = liftOptOut,
                        onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                        onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                        onLogin = { onLoginClick(isLoggedIn, activity) },
                        up = { findNavController().navigateUp() },
                    )
                    SortRow(
                        sortText = sortText,
                        filtersText = filtersText,
                        sortIcon = painterResource(R.drawable.baseline_sort_black_24),
                        onClick = {
                            StreamsSortDialog.newInstance(
                                sort = viewModel.sort,
                                tags = viewModel.tags,
                                languages = viewModel.languages
                            ).show(childFragmentManager, null)
                        },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            StreamsTab(
                flow = viewModel.flow,
                compact = streamsCompact(followedContent = false),
                showGame = true,
                enableScrollTop = enableScrollTop,
                bottomInset = bottomInset,
                portrait = portrait,
                refreshTick = refreshTick,
                scrollTick = scrollTick,
                modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                onStreamClick = { activity.startStream(it) },
                onChannelClick = ::openChannel,
                onGameClick = ::openGame,
                onTagClick = ::addTag,
                onIntegrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) },
            )
        }
    }

    private fun onLoginClick(isLoggedIn: Boolean, activity: MainActivity) {
        if (isLoggedIn) {
            activity.getAlertDialogBuilder().apply {
                setTitle(getString(R.string.logout_title))
                requireContext().tokenPrefs().getString(C.USERNAME, null)?.let { setMessage(getString(R.string.logout_msg, it)) }
                setNegativeButton(getString(R.string.no), null)
                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java)) }
            }.show()
        } else {
            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = viewModel.getGameSort("top_streams")
                viewModel.setFilter(
                    sort = sortValues?.streamSort,
                    tags = args.tags ?: sortValues?.streamTags?.split(',')?.toTypedArray(),
                    languages = args.languages ?: sortValues?.streamLanguages?.split(',')?.toTypedArray(),
                )
                viewModel.sortText.value = getString(
                    R.string.sort_by,
                    getString(
                        when (viewModel.sort) {
                            SORT_VIEWERS -> R.string.viewers_high
                            SORT_VIEWERS_ASC -> R.string.viewers_low
                            RECENT -> R.string.recent
                            else -> R.string.viewers_high
                        }
                    )
                )
                viewModel.filtersText.value = filtersText()
            }
            // No adapter: StreamsTab collects viewModel.flow and owns load states.
        }
    }

    private fun filtersText(): CharSequence? = if (viewModel.tags.isNotEmpty() || viewModel.languages.isNotEmpty()) {
        buildString {
            if (viewModel.tags.isNotEmpty()) {
                append(
                    resources.getQuantityString(
                        R.plurals.tags,
                        viewModel.tags.size,
                        viewModel.tags.joinToString()
                    )
                )
            }
            if (viewModel.languages.isNotEmpty()) {
                if (isNotEmpty()) {
                    append(". ")
                }
                append(
                    resources.getQuantityString(
                        R.plurals.languages,
                        viewModel.languages.size,
                        viewModel.languages.joinToString()
                    )
                )
            }
        }
    } else null

    private fun openChannel(item: Stream) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = item.channelId,
                channelLogin = item.channelLogin,
                channelName = item.channelName,
                channelImage = item.channelImage,
                streamId = item.id,
            )
        )
    }

    private fun openGame(item: Stream) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = item.gameId,
                    gameSlug = item.gameSlug,
                    gameName = item.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = item.gameId,
                    gameSlug = item.gameSlug,
                    gameName = item.gameName,
                )
            }
        )
    }

    private fun addTag(tag: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // New filter emits a new PagingData via flatMapLatest; Compose reloads.
            viewModel.setFilter(viewModel.sort, viewModel.tags.plus(tag).sortedArray(), viewModel.languages)
            viewModel.filtersText.value = filtersText()
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, tags: Array<String>, languages: Array<String>, changed: Boolean, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (changed) {
                viewModel.setFilter(sort, tags, languages)
                viewModel.sortText.value = getString(R.string.sort_by, sortText)
                viewModel.filtersText.value = filtersText()
            }
            if (saveFilters && (tags.isNotEmpty() || languages.isNotEmpty())) {
                viewModel.saveFilters(
                    SavedFilter(
                        tags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                        languages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    )
                )
            }
            if (saveDefault) {
                val item = viewModel.getGameSort("top_streams")?.apply {
                    streamSort = sort
                    streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
                    streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                } ?: GameSort(
                    id = "top_streams",
                    streamSort = sort,
                    streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                    streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                )
                viewModel.saveGameSort(item)
            }
        }
    }

    override fun deleteSavedSort() {
    }

    override fun scrollToTop() {
        composeScrollTopSignal.value++
    }

    override fun onNetworkRestored() {
        composeRefreshSignal.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                composeRefreshSignal.value++
            }
        }
    }
}
