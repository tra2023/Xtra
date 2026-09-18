package com.github.andreyasadchy.xtra.ui.search.games

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.common.GamesTab
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.RecentSearchList
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragment
import com.github.andreyasadchy.xtra.ui.search.Searchable
import com.github.andreyasadchy.xtra.ui.search.games.GameSearchViewModel.Companion.GameSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Game search as Compose: the shared games list, or the recent searches while
 * the query is empty.
 */
class GameSearchFragment : PagedListFragment(), Searchable {

    private val viewModel: GameSearchViewModel by viewModels { GameSearchViewModelFactory }
    private val composeRefreshSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    SearchScreen()
                }
            }
        }
    }

    @Composable
    private fun SearchScreen() {
        val activity = requireActivity() as MainActivity
        val query by viewModel.query.collectAsState()
        if (query.isBlank() && requireContext().prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)) {
            val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
            RecentSearchList(
                searches = recentSearches,
                onSelect = { (parentFragment as? SearchPagerFragment)?.setQuery(it) },
                onDelete = { viewModel.deleteRecentSearch(it) },
            )
        } else {
            val refreshTick by composeRefreshSignal.collectAsState()
            GamesTab(
                flow = viewModel.flow,
                bottomInset = xtraBottomInset(activity),
                portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
                refreshTick = refreshTick,
                scrollTick = 0,
                modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                diskCache = true,
                onTagClick = ::openTag,
                onClick = ::openGame,
                onIntegrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) },
            )
        }
    }

    override fun initialize() {
        // No adapter: GamesTab collects viewModel.flow and owns load states.
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

    private fun openTag(tag: Tag) {
        findNavController().navigate(
            GamesFragmentDirections.actionGlobalGamesFragment(
                tagIds = listOfNotNull(tag.id).toTypedArray(),
                tagNames = listOfNotNull(tag.name).toTypedArray(),
            )
        )
    }

    override fun search(query: String) {
        viewModel.setQuery(query)
        if (requireContext().prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)) {
            viewModel.saveRecentSearch(query)
        }
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
