package com.github.andreyasadchy.xtra.ui.channel.videos

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.SortBarBinding
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.channel.videos.ChannelVideosViewModel.Companion.ChannelVideosViewModelFactory
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.common.VideosTab
import com.github.andreyasadchy.xtra.ui.common.positionFor
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Channel videos as Compose: the shared videos list with its own sort bar, which
 * the channel pager still hands in as a `SortBarBinding`.
 */
class ChannelVideosFragment : PagedListFragment(), Scrollable, Sortable, VideosSortDialog.OnFilter {

    private val args: ChannelPagerFragmentArgs by navArgs()
    private val viewModel: ChannelVideosViewModel by viewModels { ChannelVideosViewModelFactory }
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                ProvideXtraLocals(activity) {
                    XtraTheme(themeId = theme) {
                        VideosScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun VideosScreen() {
        val activity = requireActivity() as MainActivity
        val refreshTick by composeRefreshSignal.collectAsState()
        val scrollTick by composeScrollTopSignal.collectAsState()
        val positions by viewModel.positions.collectAsState(initial = null)
        val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
        val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
        VideosTab(
            flow = viewModel.flow,
            showGame = true,
            bottomInset = xtraBottomInset(activity),
            portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
            refreshTick = refreshTick,
            scrollTick = scrollTick,
            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
            showChannel = false,
            positionFor = { positions.positionFor(it) },
            isBookmarked = { it in bookmarkIds },
            onDownload = ::showDownloadDialog,
            onBookmark = ::saveBookmark,
            onChannelClick = ::openChannel,
            onGameClick = ::openGame,
            onIntegrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) },
        )
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = args.channelId?.let { viewModel.getChannelSort(it) } ?: viewModel.getChannelSort("default")
                viewModel.setFilter(
                    sort = sortValues?.videoSort,
                    type = sortValues?.videoType,
                )
                viewModel.sortText.value = getString(
                    R.string.sort_and_type,
                    getString(
                        when (viewModel.sort) {
                            VideosSortDialog.SORT_TIME -> R.string.upload_date
                            VideosSortDialog.SORT_VIEWS -> R.string.view_count
                            else -> R.string.upload_date
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
            }
            // No adapter: VideosTab collects viewModel.flow and owns load states.
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
                    viewModel.setFilter(sort, type)
                    viewModel.sortText.value = getString(R.string.sort_and_type, sortText, typeText)
                }
                if (saveSort) {
                    args.channelId?.let { id ->
                        val item = viewModel.getChannelSort(id)?.apply {
                            videoSort = sort
                            videoType = type
                        } ?: ChannelSort(
                            id = id,
                            videoSort = sort,
                            videoType = type
                        )
                        viewModel.saveChannelSort(item)
                    }
                }
                if (saveDefault) {
                    val item = viewModel.getChannelSort("default")?.apply {
                        videoSort = sort
                        videoType = type
                    } ?: ChannelSort(
                        id = "default",
                        videoSort = sort,
                        videoType = type
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
}
