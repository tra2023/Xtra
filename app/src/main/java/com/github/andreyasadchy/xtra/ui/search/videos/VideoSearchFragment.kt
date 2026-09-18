package com.github.andreyasadchy.xtra.ui.search.videos

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
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.VideosTab
import com.github.andreyasadchy.xtra.ui.common.positionFor
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.RecentSearchList
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragment
import com.github.andreyasadchy.xtra.ui.search.Searchable
import com.github.andreyasadchy.xtra.ui.search.videos.VideoSearchViewModel.Companion.VideoSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Video search as Compose: the shared videos list, or the recent searches while
 * the query is empty.
 */
class VideoSearchFragment : PagedListFragment(), Searchable {

    private val viewModel: VideoSearchViewModel by viewModels { VideoSearchViewModelFactory }
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
            val positions by viewModel.positions.collectAsState(initial = null)
            val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
            val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
            VideosTab(
                flow = viewModel.flow,
                showGame = true,
                bottomInset = xtraBottomInset(activity),
                portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
                refreshTick = refreshTick,
                scrollTick = 0,
                modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                positionFor = { positions.positionFor(it) },
                isBookmarked = { it in bookmarkIds },
                onDownload = ::showDownloadDialog,
                onBookmark = ::saveBookmark,
                onChannelClick = ::openChannel,
                onGameClick = ::openGame,
                onIntegrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) },
            )
        }
    }

    override fun initialize() {
        // No adapter: VideosTab collects viewModel.flow and owns load states.
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
