package com.github.andreyasadchy.xtra.ui.search.channels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.RecentSearchCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.UserListItem
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragment
import com.github.andreyasadchy.xtra.ui.search.Searchable
import com.github.andreyasadchy.xtra.ui.search.channels.ChannelSearchViewModel.Companion.ChannelSearchViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow

class ChannelSearchFragment : PagedListFragment(), Searchable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChannelSearchViewModel by viewModels { ChannelSearchViewModelFactory }
    // Compose owns list + load states now (PagedListFragment.PagingContent): signals drive refresh / scroll-top.
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Compose owns list + load states. The hidden RecyclerView container keeps
        // its overlays off; refresh gesture + scroll-top live in Compose now.
        binding.recyclerView.isVisible = false
        binding.progressBar.isVisible = false
        binding.nothingHere.isVisible = false
        binding.scrollTop.isVisible = false
        binding.swipeRefresh.isEnabled = false
        val composeView = createPagingView()
        (binding.root as ViewGroup).addView(
            composeView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pagingContent = {
            val query by viewModel.query.collectAsState()
            val context = LocalContext.current
            if (query.isBlank() && context.prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)) {
                val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
                LazyColumn {
                    items(recentSearches, key = { it.query }) { search ->
                        RecentSearchCollectionRow(
                            query = search.query,
                            historyIcon = painterResource(R.drawable.baseline_history_black_24),
                            deleteIcon = painterResource(R.drawable.baseline_delete_black_24),
                            deleteLabel = stringResource(R.string.delete),
                            onClick = { (parentFragment as? SearchPagerFragment)?.setQuery(search.query) },
                            onDelete = { viewModel.deleteRecentSearch(search) },
                        )
                    }
                }
            } else {
                val refreshTick by composeRefreshSignal.collectAsState()
                val scrollTick by composeScrollTopSignal.collectAsState()
                PagingContent(
                    flow = viewModel.flow,
                    refreshSignal = refreshTick,
                    retrySignal = 0,
                    scrollTopSignal = scrollTick,
                    enableScrollTop = false,
                    keyForItem = { it.id ?: it.login ?: it.hashCode().toString() },
                ) { user ->
                    UserListItem(
                        user = user,
                        details = listOfNotNull(context.collectionCount(user.followerCount, R.plurals.followers)),
                        labels = listOfNotNull(context.getString(R.string.live).takeIf { user.isLive == true }),
                        onClick = ::openChannel,
                    )
                }
            }
        }
    }

    override fun initialize() {
        // No adapter: pagingContent collects viewModel.flow and owns load states.
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
