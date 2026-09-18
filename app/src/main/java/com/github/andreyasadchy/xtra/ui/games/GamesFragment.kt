package com.github.andreyasadchy.xtra.ui.games

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
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.common.GamesTab
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.SortRow
import com.github.andreyasadchy.xtra.ui.common.XtraTopBar
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesViewModel.Companion.GamesViewModelFactory
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Games browser as a full-Compose screen: app bar, sort row and the shared games
 * list from `:core:ui`. Only the filters ever show in the sort row.
 */
class GamesFragment : PagedListFragment(), Scrollable, GamesSortDialog.OnFilter {

    private val args: GamesFragmentArgs by navArgs()
    private val viewModel: GamesViewModel by viewModels { GamesViewModelFactory }
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            id = R.id.swipeRefresh
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    GamesScreen()
                }
            }
        }
    }

    @Composable
    private fun GamesScreen() {
        val activity = requireActivity() as MainActivity
        val isLoggedIn = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        val liftOptOut = !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        val bottomInset = xtraBottomInset(activity)
        val refreshTick by composeRefreshSignal.collectAsState()
        val scrollTick by composeScrollTopSignal.collectAsState()
        val filtersText by viewModel.filtersText.collectAsState()
        Scaffold(
            topBar = {
                Column {
                    XtraTopBar(
                        title = stringResource(R.string.games),
                        isLoggedIn = isLoggedIn,
                        liftOptOut = liftOptOut,
                        onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                        onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                        onLogin = { onLoginClick(isLoggedIn, activity) },
                        up = { findNavController().navigateUp() },
                    )
                    SortRow(
                        sortText = null,
                        filtersText = filtersText,
                        sortIcon = painterResource(R.drawable.baseline_sort_black_24),
                        onClick = { onSortClick() },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            GamesTab(
                flow = viewModel.flow,
                bottomInset = bottomInset,
                portrait = portrait,
                refreshTick = refreshTick,
                scrollTick = scrollTick,
                modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                diskCache = true,
                onTagClick = ::addTag,
                onClick = ::openGame,
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

    private fun onSortClick() {
        val tags = viewModel.tags.mapNotNull { tag ->
            val id = tag.id
            val name = tag.name
            if (id != null && name != null) {
                id to name
            } else null
        }.toMap()
        GamesSortDialog.newInstance(
            tagIds = tags.keys.toTypedArray(),
            tagNames = tags.values.toTypedArray(),
        ).show(childFragmentManager, null)
    }

    private fun openGame(game: Game) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    boxArt = game.boxArt,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    boxArt = game.boxArt,
                )
            }
        )
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                viewModel.setFilter(viewModel.tags.ifEmpty { navTags() })
                viewModel.filtersText.value = filtersText()
            }
            // No adapter: GamesTab collects viewModel.flow and owns load states.
        }
    }

    private fun navTags(): Array<Tag>? {
        val ids = args.tagIds
        if (ids.isNullOrEmpty()) return null
        val names = args.tagNames
        return ids.mapIndexed { index, id -> Tag(id = id, name = names?.getOrNull(index)) }.toTypedArray()
    }

    private fun filtersText(): CharSequence? = if (viewModel.tags.isNotEmpty()) {
        resources.getQuantityString(
            R.plurals.tags,
            viewModel.tags.size,
            viewModel.tags.mapNotNull { it.name }.joinToString()
        )
    } else null

    private fun addTag(tag: Tag) {
        viewLifecycleOwner.lifecycleScope.launch {
            // New filter emits a new PagingData via flatMapLatest; Compose reloads.
            viewModel.setFilter(viewModel.tags.plus(tag).sortedBy { it.id }.toTypedArray())
            viewModel.filtersText.value = filtersText()
        }
    }

    override fun onChange(tags: Array<Tag>) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setFilter(tags)
            viewModel.filtersText.value = filtersText()
        }
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
