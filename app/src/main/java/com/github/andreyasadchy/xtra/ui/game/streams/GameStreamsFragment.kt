package com.github.andreyasadchy.xtra.ui.game.streams

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
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.common.StreamListItem
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.RECENT
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.SORT_VIEWERS
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.SORT_VIEWERS_ASC
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsViewModel.Companion.GameStreamsViewModelFactory
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameStreamsFragment : PagedListFragment(), Scrollable, Sortable, StreamsSortDialog.OnFilter {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: GameStreamsViewModel by viewModels { GameStreamsViewModelFactory }
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
        val compact = requireContext().prefs().getString(C.COMPACT_STREAMS, "disabled") == "all"
        val enableScrollTop = args.gameId != null || args.gameName != null || !args.tags.isNullOrEmpty()
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
                enableScrollTop = enableScrollTop,
                keyForItem = { it.id ?: it.channelId ?: it.channelLogin ?: it.hashCode().toString() },
            ) { stream ->
                StreamListItem(
                    stream = stream,
                    compact = compact,
                    showGame = false,
                    onStreamClick = { (activity as? MainActivity)?.startStream(it) },
                    onChannelClick = ::openChannel,
                    onGameClick = ::openGame,
                    onTagClick = ::addTag,
                )
            }
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = args.gameId?.let { viewModel.getGameSort(it) } ?: viewModel.getGameSort("default")
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
                viewModel.filtersText.value = if (viewModel.tags.isNotEmpty() || viewModel.languages.isNotEmpty()) {
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
            }
            // No adapter: PagingContent collects viewModel.flow and owns load states.
        }
    }

    override fun setupSortBar(sortBar: SortBarBinding) {
        sortBar.root.visibility = View.VISIBLE
        sortBar.root.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                StreamsSortDialog.newInstance(
                    sort = viewModel.sort,
                    tags = viewModel.tags,
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
            val tags = viewModel.tags.plus(tag).sortedArray()
            viewModel.setFilter(viewModel.sort, tags, viewModel.languages)
            viewModel.filtersText.value = buildString {
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
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, tags: Array<String>, languages: Array<String>, changed: Boolean, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (changed) {
                    viewModel.setFilter(sort, tags, languages)
                    viewModel.sortText.value = getString(R.string.sort_by, sortText)
                    viewModel.filtersText.value = if (viewModel.tags.isNotEmpty() || viewModel.languages.isNotEmpty()) {
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
                }
                if (saveFilters && (tags.isNotEmpty() || languages.isNotEmpty())) {
                    viewModel.saveFilters(
                        SavedFilter(
                            gameId = args.gameId,
                            gameSlug = args.gameSlug,
                            gameName = args.gameName,
                            tags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                            languages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        )
                    )
                }
                if (saveSort) {
                    args.gameId?.let { id ->
                        val item = viewModel.getGameSort(id)?.apply {
                            streamSort = sort
                            streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
                            streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        } ?: GameSort(
                            id = id,
                            streamSort = sort,
                            streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                            streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        )
                        viewModel.saveGameSort(item)
                    }
                }
                if (saveDefault) {
                    val item = viewModel.getGameSort("default")?.apply {
                        streamSort = sort
                        streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
                        streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    } ?: GameSort(
                        id = "default",
                        streamSort = sort,
                        streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                        streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
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
