package com.github.andreyasadchy.xtra.ui.game.videos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.common.VideoListItem
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.common.positionFor
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosViewModel.Companion.GameVideosViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameVideosFragment : PagedListFragment(), Scrollable, Sortable, VideosSortDialog.OnFilter {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: GameVideosViewModel by viewModels { GameVideosViewModelFactory }
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
            val positions by viewModel.positions.collectAsState(initial = null)
            val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
            val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
            PagingContent(
                flow = viewModel.flow,
                refreshSignal = refreshTick,
                retrySignal = 0,
                scrollTopSignal = scrollTick,
                keyForItem = { it.id ?: it.hashCode().toString() },
            ) { video ->
                VideoListItem(
                    video = video,
                    position = positions.positionFor(video.id),
                    bookmarked = video.id in bookmarkIds,
                    showGame = false,
                    onDownload = ::showDownloadDialog,
                    onBookmark = ::saveBookmark,
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
                    sort = sortValues?.videoSort,
                    period = if (!TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()) {
                        sortValues?.videoPeriod
                    } else null,
                    type = sortValues?.videoType,
                    languages = sortValues?.videoLanguages?.split(',')?.toTypedArray(),
                )
                viewModel.sortText.value = getString(
                    R.string.sort_and_type,
                    getString(
                        when (viewModel.sort) {
                            VideosSortDialog.SORT_TIME -> R.string.upload_date
                            VideosSortDialog.SORT_VIEWS -> R.string.view_count
                            else -> R.string.view_count
                        }
                    ),
                    getString(
                        when (viewModel.type) {
                            VideosSortDialog.VIDEO_TYPE_ARCHIVE -> R.string.video_type_archive
                            VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> R.string.video_type_highlight
                            VideosSortDialog.VIDEO_TYPE_UPLOAD -> R.string.video_type_upload
                            else -> R.string.all
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
                    sort = viewModel.sort,
                    period = viewModel.period,
                    type = viewModel.type,
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

    private fun showDownloadDialog(video: Video) {
        DownloadDialog.newVideoInstance(
            id = video.id,
            channelId = video.channelId,
            channelLogin = video.channelLogin,
            channelName = video.channelName,
            channelImage = video.channelImage,
            gameId = video.gameId,
            gameSlug = video.gameSlug,
            gameName = video.gameName,
            title = video.title,
            thumbnail = video.thumbnail,
            createdAt = video.createdAt,
            durationSeconds = video.durationSeconds,
            type = video.type,
            animatedPreviewUrl = video.animatedPreviewURL,
        ).show(childFragmentManager, null)
    }

    private fun saveBookmark(video: Video) {
        viewModel.saveBookmark(
            requireContext().filesDir.path,
            video,
            TwitchApiHelper.getGQLHeaders(requireContext()),
            TwitchApiHelper.getHelixHeaders(requireContext()),
        )
    }

    private fun openChannel(video: Video) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = video.channelId,
                channelLogin = video.channelLogin,
                channelName = video.channelName,
                channelImage = video.channelImage,
            )
        )
    }

    private fun openGame(video: Video) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = video.gameId,
                    gameSlug = video.gameSlug,
                    gameName = video.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = video.gameId,
                    gameSlug = video.gameSlug,
                    gameName = video.gameName,
                )
            }
        )
    }

    override fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (changed) {
                    viewModel.setFilter(sort, period, type, languages)
                    viewModel.sortText.value = getString(R.string.sort_and_type, sortText, typeText)
                    viewModel.filtersText.value = if (languages.isNotEmpty()) {
                        resources.getQuantityString(R.plurals.languages, languages.size, languages.joinToString())
                    } else null
                }
                if (saveSort) {
                    args.gameId?.let { id ->
                        val item = viewModel.getGameSort(id)?.apply {
                            videoSort = sort
                            if (!TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()) {
                                videoPeriod = period
                            }
                            videoType = type
                            videoLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        } ?: GameSort(
                            id = id,
                            videoSort = sort,
                            videoPeriod = if (!TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()) period else null,
                            videoType = type,
                            videoLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        )
                        viewModel.saveGameSort(item)
                    }
                }
                if (saveDefault) {
                    val item = viewModel.getGameSort("default")?.apply {
                        videoSort = sort
                        if (!TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()) {
                            videoPeriod = period
                        }
                        videoType = type
                        videoLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    } ?: GameSort(
                        id = "default",
                        videoSort = sort,
                        videoPeriod = if (!TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()) period else null,
                        videoType = type,
                        videoLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
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
