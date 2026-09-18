package com.github.andreyasadchy.xtra.ui.channel.clips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.databinding.SortBarBinding
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.channel.clips.ChannelClipsViewModel.Companion.ChannelClipsViewModelFactory
import com.github.andreyasadchy.xtra.ui.common.ClipListItem
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChannelClipsFragment : PagedListFragment(), Scrollable, Sortable, VideosSortDialog.OnFilter {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val args: ChannelPagerFragmentArgs by navArgs()
    private val viewModel: ChannelClipsViewModel by viewModels { ChannelClipsViewModelFactory }
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
            PagingContent(
                flow = viewModel.flow,
                refreshSignal = refreshTick,
                retrySignal = 0,
                scrollTopSignal = scrollTick,
                keyForItem = { it.id ?: it.hashCode().toString() },
            ) { clip ->
                ClipListItem(
                    clip = clip,
                    showChannel = false,
                    onDownload = ::showDownloadDialog,
                    onChannelClick = ::openChannel,
                    onGameClick = ::openGame,
                )
            }
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = args.channelId?.let { viewModel.getChannelSort(it) } ?: viewModel.getChannelSort("default")
                viewModel.setFilter(
                    period = sortValues?.clipPeriod,
                )
                viewModel.sortText.value = getString(
                    R.string.sort_and_period,
                    getString(R.string.view_count),
                    getString(
                        when (viewModel.period) {
                            VideosSortDialog.PERIOD_DAY -> R.string.today
                            VideosSortDialog.PERIOD_WEEK -> R.string.this_week
                            VideosSortDialog.PERIOD_MONTH -> R.string.this_month
                            VideosSortDialog.PERIOD_ALL -> R.string.all_time
                            else -> R.string.this_week
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
            viewLifecycleOwner.lifecycleScope.launch {
                VideosSortDialog.newInstance(
                    sort = VideosSortDialog.SORT_VIEWS,
                    period = viewModel.period,
                    saved = args.channelId?.let { viewModel.getChannelSort(it) } != null
                ).show(childFragmentManager, null)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sortText.collectLatest {
                    sortBar.sortText.text = it
                }
            }
        }
    }

    private fun showDownloadDialog(clip: Clip) {
        DownloadDialog.newClipInstance(
            id = clip.id,
            channelId = clip.channelId,
            channelLogin = clip.channelLogin,
            channelName = clip.channelName,
            channelImage = clip.channelImage,
            gameId = clip.gameId,
            gameSlug = clip.gameSlug,
            gameName = clip.gameName,
            title = clip.title,
            thumbnail = clip.thumbnail,
            createdAt = clip.createdAt,
            durationSeconds = clip.durationSeconds,
            videoId = clip.videoId,
            videoOffsetSeconds = clip.videoOffsetSeconds,
            videoCreatedAt = clip.videoCreatedAt,
        ).show(childFragmentManager, null)
    }

    private fun openChannel(clip: Clip) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = clip.channelId,
                channelLogin = clip.channelLogin,
                channelName = clip.channelName,
                channelImage = clip.channelImage,
            )
        )
    }

    private fun openGame(clip: Clip) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = clip.gameId,
                    gameSlug = clip.gameSlug,
                    gameName = clip.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = clip.gameId,
                    gameSlug = clip.gameSlug,
                    gameName = clip.gameName,
                )
            }
        )
    }

    override fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (changed) {
                    viewModel.setFilter(period)
                    viewModel.sortText.value = getString(R.string.sort_and_period, sortText, periodText)
                }
                if (saveSort) {
                    args.channelId?.let { id ->
                        val item = viewModel.getChannelSort(id)?.apply {
                            clipPeriod = period
                        } ?: ChannelSort(
                            id = id,
                            clipPeriod = period
                        )
                        viewModel.saveChannelSort(item)
                    }
                }
                if (saveDefault) {
                    val item = viewModel.getChannelSort("default")?.apply {
                        clipPeriod = period
                    } ?: ChannelSort(
                        id = "default",
                        clipPeriod = period
                    )
                    viewModel.saveChannelSort(item)
                }
            }
        }
    }

    override fun deleteSavedSort() {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                args.channelId?.let { viewModel.getChannelSort(it) }?.let { viewModel.deleteChannelSort(it) }
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
        (parentFragment as? IntegrityDialog.Listener)?.onIntegrityTokenLoaded("refresh")
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
