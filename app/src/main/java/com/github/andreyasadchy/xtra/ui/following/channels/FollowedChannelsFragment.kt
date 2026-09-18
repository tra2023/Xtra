package com.github.andreyasadchy.xtra.ui.following.channels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.databinding.SortBarBinding
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.collectionFollowLabels
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.common.UserListItem
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsViewModel.Companion.FollowedChannelsViewModelFactory
import com.github.andreyasadchy.xtra.util.formatChatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Instant

class FollowedChannelsFragment : PagedListFragment(), Scrollable, Sortable, FollowedChannelsSortDialog.OnFilter {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FollowedChannelsViewModel by viewModels { FollowedChannelsViewModelFactory }
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
            val refreshTick by composeRefreshSignal.collectAsState()
            val scrollTick by composeScrollTopSignal.collectAsState()
            val context = LocalContext.current
            PagingContent(
                flow = viewModel.flow,
                refreshSignal = refreshTick,
                retrySignal = 0,
                scrollTopSignal = scrollTick,
                enableScrollTop = false,
                keyForItem = { it.id ?: it.login ?: it.hashCode().toString() },
            ) { user ->
                fun date(value: String?, label: Int): String? = value?.let {
                    Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }?.let { time ->
                        context.getString(label, formatChatDate(time))
                    }
                }
                UserListItem(
                    user = user,
                    details = listOfNotNull(
                        date(user.lastBroadcast, R.string.last_broadcast_date),
                        date(user.followedAt, R.string.followed_at),
                    ),
                    labels = context.collectionFollowLabels(user.accountFollow, user.localFollow),
                    onClick = ::openChannel,
                )
            }
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = viewModel.getChannelSort("followed_channels")
                viewModel.setFilter(
                    sort = sortValues?.videoSort,
                    order = sortValues?.videoType,
                )
                viewModel.sortText.value = getString(
                    R.string.sort_and_order,
                    getString(
                        when (viewModel.sort) {
                            FollowedChannelsSortDialog.SORT_FOLLOWED_AT -> R.string.time_followed
                            FollowedChannelsSortDialog.SORT_ALPHABETICALLY -> R.string.alphabetically
                            FollowedChannelsSortDialog.SORT_LAST_BROADCAST -> R.string.last_broadcast
                            else -> R.string.last_broadcast
                        }
                    ),
                    getString(
                        when (viewModel.order) {
                            FollowedChannelsSortDialog.ORDER_DESC -> R.string.descending
                            FollowedChannelsSortDialog.ORDER_ASC -> R.string.ascending
                            else -> R.string.descending
                        }
                    )
                )
            }
            // No adapter: PagingContent collects viewModel.flow and owns load states.
        }
    }

    override fun setupSortBar(sortBar: SortBarBinding) {
        sortBar.root.visibility = View.VISIBLE
        sortBar.root.setOnClickListener {
            FollowedChannelsSortDialog.newInstance(
                sort = viewModel.sort,
                order = viewModel.order,
            ).show(childFragmentManager, null)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sortText.collectLatest {
                    sortBar.sortText.text = it
                }
            }
        }
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

    override fun onChange(sort: String, sortText: CharSequence, order: String, orderText: CharSequence, changed: Boolean, saveDefault: Boolean) {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (changed) {
                    viewModel.setFilter(sort, order)
                    viewModel.sortText.value = getString(R.string.sort_and_order, sortText, orderText)
                }
                if (saveDefault) {
                    val item = viewModel.getChannelSort("followed_channels")?.apply {
                        videoSort = sort
                        videoType = order
                    } ?: ChannelSort(
                        id = "followed_channels",
                        videoSort = sort,
                        videoType = order
                    )
                    viewModel.saveChannelSort(item)
                }
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
