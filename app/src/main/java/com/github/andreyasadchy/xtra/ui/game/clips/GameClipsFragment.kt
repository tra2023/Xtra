package com.github.andreyasadchy.xtra.ui.game.clips

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
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.ClipListItem
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsViewModel.Companion.GameClipsViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameClipsFragment : PagedListFragment(), Scrollable, Sortable, VideosSortDialog.OnFilter {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: GameClipsViewModel by viewModels { GameClipsViewModelFactory }
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
                    showGame = false,
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
                val sortValues = args.gameId?.let { viewModel.getGameSort(it) } ?: viewModel.getGameSort("default")
                viewModel.setFilter(
                    period = sortValues?.clipPeriod,
                    languages = sortValues?.clipLanguages?.split(',')?.toTypedArray(),
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
                viewModel.filtersText.value = if (viewModel.languages.isNotEmpty()) {
                    resources.getQuantityString(R.plurals.languages, viewModel.languages.size, viewModel.languages.joinToString())
                } else null
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
                    languages = viewModel.languages,
                    saved = args.gameId?.let { viewModel.getGameSort(it) } != null
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
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filtersText.collectLatest {
                    if (it != null) {
                        sortBar.filtersText.visibility = View.VISIBLE
                        sortBar.filtersText.text = it
                    } else {
                        sortBar.filtersText.visibility = View.GONE
                    }
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
                    viewModel.setFilter(period, languages)
                    viewModel.sortText.value = getString(R.string.sort_and_period, sortText, periodText)
                    viewModel.filtersText.value = if (languages.isNotEmpty()) {
                        resources.getQuantityString(R.plurals.languages, languages.size, languages.joinToString())
                    } else null
                }
                if (saveSort) {
                    args.gameId?.let { id ->
                        val item = viewModel.getGameSort(id)?.apply {
                            clipPeriod = period
                            clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        } ?: GameSort(
                            id = id,
                            clipPeriod = period,
                            clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        )
                        viewModel.saveGameSort(item)
                    }
                }
                if (saveDefault) {
                    val item = viewModel.getGameSort("default")?.apply {
                        clipPeriod = period
                        clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    } ?: GameSort(
                        id = "default",
                        clipPeriod = period,
                        clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    )
                    viewModel.saveGameSort(item)
                }
            }
        }
    }

    override fun deleteSavedSort() {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                args.gameId?.let { viewModel.getGameSort(it) }?.let { viewModel.deleteGameSort(it) }
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
