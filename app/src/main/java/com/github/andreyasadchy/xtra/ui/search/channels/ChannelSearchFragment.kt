package com.github.andreyasadchy.xtra.ui.search.channels

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
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.ui.common.ChannelsTab
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.RecentSearchList
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragment
import com.github.andreyasadchy.xtra.ui.search.Searchable
import com.github.andreyasadchy.xtra.ui.search.channels.ChannelSearchViewModel.Companion.ChannelSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Channel search as Compose: the shared channels list, or the recent searches
 * while the query is empty.
 */
class ChannelSearchFragment : PagedListFragment(), Searchable {

    private val viewModel: ChannelSearchViewModel by viewModels { ChannelSearchViewModelFactory }
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
        val context = requireContext()
        val query by viewModel.query.collectAsState()
        if (query.isBlank() && context.prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)) {
            val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
            RecentSearchList(
                searches = recentSearches,
                onSelect = { (parentFragment as? SearchPagerFragment)?.setQuery(it) },
                onDelete = { viewModel.deleteRecentSearch(it) },
            )
        } else {
            val refreshTick by composeRefreshSignal.collectAsState()
            ChannelsTab(
                flow = viewModel.flow,
                bottomInset = xtraBottomInset(activity),
                portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
                refreshTick = refreshTick,
                scrollTick = 0,
                modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                detailsFor = { listOfNotNull(context.collectionCount(it.followerCount, R.plurals.followers)) },
                labelsFor = { user -> listOfNotNull(context.getString(R.string.live).takeIf { user.isLive == true }) },
                onClick = ::openChannel,
                onIntegrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) },
            )
        }
    }

    override fun initialize() {
        // No adapter: ChannelsTab collects viewModel.flow and owns load states.
    }

    private fun openChannel(user: User) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = user.id,
                channelLogin = user.login,
                channelName = user.name,
                channelImage = user.profileImage,
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
