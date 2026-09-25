package com.github.andreyasadchy.xtra.ui.game

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.ClipsTab
import com.github.andreyasadchy.xtra.ui.common.CollapsingBanner
import com.github.andreyasadchy.xtra.ui.common.CollapsingHeaderState
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.SortRow
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog
import com.github.andreyasadchy.xtra.ui.common.StreamsTab
import com.github.andreyasadchy.xtra.ui.common.TabDropdown
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.common.VideosTab
import com.github.andreyasadchy.xtra.ui.common.XtraTopBar
import com.github.andreyasadchy.xtra.ui.common.positionFor
import com.github.andreyasadchy.xtra.ui.common.rememberCollapseConnection
import com.github.andreyasadchy.xtra.ui.common.streamsCompact
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GamePagerViewModel.Companion.GamePagerViewModelFactory
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsViewModel
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsViewModel.Companion.GameClipsViewModelFactory
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsViewModel
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsViewModel.Companion.GameStreamsViewModelFactory
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosViewModel
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosViewModel.Companion.GameVideosViewModelFactory
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.defaultTabIndex
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.parseEnabledTabs
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Shared host for the two game screens as a full-Compose scaffold: collapsing
 * banner, tab selector and sort row on top, one shared nested-scroll system
 * underneath. The fragment stays the host
 * for navigation, dialogs, launchers and ViewModels; tab ViewModels are
 * parent-scoped by key.
 *
 * Subclasses only pick the tab selector: [GamePagerFragment] renders a tab row,
 * [GameMediaFragment] (the "use tabs for the Games page" opt-out) renders a
 * dropdown. Everything else lives here exactly once.
 */
@OptIn(ExperimentalMaterial3Api::class)
abstract class BaseGameFragment : PagedListFragment(), Scrollable, StreamsSortDialog.OnFilter, VideosSortDialog.OnFilter {

    /** Tab row when true, dropdown chooser (no swipe) when false. */
    protected abstract val useTabs: Boolean

    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: GamePagerViewModel by viewModels { GamePagerViewModelFactory }
    private val videosViewModel by lazy { ViewModelProvider(this, GameVideosViewModelFactory)["videos", GameVideosViewModel::class.java] }
    private val streamsViewModel by lazy { ViewModelProvider(this, GameStreamsViewModelFactory)["streams", GameStreamsViewModel::class.java] }
    private val clipsViewModel by lazy { ViewModelProvider(this, GameClipsViewModelFactory)["clips", GameClipsViewModel::class.java] }

    private val videosRefresh = MutableStateFlow(0)
    private val videosScrollTop = MutableStateFlow(0)
    private val streamsRefresh = MutableStateFlow(0)
    private val streamsScrollTop = MutableStateFlow(0)
    private val clipsRefresh = MutableStateFlow(0)
    private val clipsScrollTop = MutableStateFlow(0)

    private var tabs: List<String> = listOf("1")
    private var initialTabIndex: Int = 0
    private var currentTabIndex by mutableIntStateOf(0)
    private val headerState = CollapsingHeaderState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = computeTabs()
        initialTabIndex = computeInitialTab()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                ProvideXtraLocals(activity) {
                    XtraTheme(themeId = theme) {
                        GameScreen()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collect {
                    (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                }
            }
        }
    }

    override fun initialize() {
        viewModel.loadGame(
            TwitchApiHelper.getGQLHeaders(requireContext()),
            TwitchApiHelper.getHelixHeaders(requireContext()),
            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
        val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        if (setting < 2) {
            viewModel.isFollowingGame(
                args.gameId,
                setting,
                TwitchApiHelper.getGQLHeaders(requireContext(), true),
            )
        }
        if (args.updateLocal) {
            viewModel.updateLocalGame(
                requireContext().filesDir.path,
                args.gameId,
                args.gameName,
                TwitchApiHelper.getGQLHeaders(requireContext()),
                TwitchApiHelper.getHelixHeaders(requireContext()),
            )
        }
        initializeStreamsTab()
        initializeVideosTab()
        initializeClipsTab()
    }

    private fun computeTabs(): List<String> {
        return parseEnabledTabs(
            requireContext().prefs().getString(C.UI_GAME_TABS, null),
            C.DEFAULT_GAME_TABS,
        )
    }

    private fun computeInitialTab(): Int {
        return defaultTabIndex(
            tabs,
            requireContext().prefs().getString(C.UI_GAME_TABS, null),
            C.DEFAULT_GAME_TABS,
            "1",
        )
    }

    @Composable
    private fun GameScreen() {
        val pagerState = rememberPagerState(initialPage = initialTabIndex) { tabs.size }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { currentTabIndex = it }
        }
        val collapseConnection = rememberCollapseConnection(headerState)
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        // Legacy View-hosted tabs still need the interop connection; the shared
        // tab composables take it as a parameter so core/ui stays platform-free.
        val tabModifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
        val activity = requireActivity() as MainActivity
        val isLoggedIn = remember {
            !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        }
        val followSetting = remember { requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0 }
        val isFollowing by viewModel.isFollowing.collectAsState()
        val followResult by viewModel.follow.collectAsState()
        LaunchedEffect(followResult) {
            followResult?.let { (following, errorMessage) ->
                if (!errorMessage.isNullOrBlank()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                } else {
                    if (following) {
                        Toast.makeText(requireContext(), getString(R.string.now_following, args.gameName), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.unfollowed, args.gameName), Toast.LENGTH_SHORT).show()
                    }
                }
                viewModel.follow.value = null
            }
        }
        val liftOptOut = remember { !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true) }
        val bottomInset = xtraBottomInset(activity)
        Scaffold(
            modifier = Modifier.nestedScroll(collapseConnection),
            topBar = {
                Column {
                    XtraTopBar(
                        title = args.gameName.orEmpty(),
                        isLoggedIn = isLoggedIn,
                        liftOptOut = liftOptOut,
                        onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                        onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                        onLogin = { onLoginClick(isLoggedIn, activity) },
                        up = { findNavController().navigateUp() },
                        actions = {
                            if (followSetting < 2) {
                                IconButton(onClick = { onFollowClick() }) {
                                    Icon(
                                        painterResource(if (isFollowing == true) R.drawable.baseline_favorite_black_24 else R.drawable.baseline_favorite_border_black_24),
                                        contentDescription = stringResource(if (isFollowing == true) R.string.unfollow else R.string.follow),
                                    )
                                }
                            }
                        },
                    )
                    val game by viewModel.game.collectAsState()
                    CollapsingBanner(headerState) {
                        GameBannerContent(
                            game = game,
                            fallbackName = args.gameName,
                            fallbackArt = args.boxArt,
                            onTagClick = ::openTag,
                        )
                    }
                    val tabTitles = tabs.map { tabId ->
                        when (tabId) {
                            "0" -> stringResource(R.string.videos)
                            "1" -> stringResource(R.string.live)
                            "2" -> stringResource(R.string.clips)
                            else -> stringResource(R.string.live)
                        }
                    }
                    if (useTabs) {
                        SecondaryTabRow(
                            currentTabIndex,
                            Modifier,
                            TabRowDefaults.primaryContainerColor,
                            TabRowDefaults.primaryContentColor,
                            @Composable {
                                if (currentTabIndex < tabs.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(currentTabIndex)
                                    )
                                }
                            },
                            @Composable { HorizontalDivider() }) {
                            tabTitles.forEachIndexed { index, title ->
                                Tab(
                                    selected = currentTabIndex == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(title) },
                                )
                            }
                        }
                    } else {
                        TabDropdown(
                            titles = tabTitles,
                            selectedIndex = currentTabIndex,
                            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                    val videosSort by videosViewModel.sortText.collectAsState()
                    val videosFilters by videosViewModel.filtersText.collectAsState()
                    val streamsSort by streamsViewModel.sortText.collectAsState()
                    val streamsFilters by streamsViewModel.filtersText.collectAsState()
                    val clipsSort by clipsViewModel.sortText.collectAsState()
                    val clipsFilters by clipsViewModel.filtersText.collectAsState()
                    val (sortText, filtersText) = when (tabs.getOrNull(currentTabIndex)) {
                        "0" -> videosSort to videosFilters
                        "1" -> streamsSort to streamsFilters
                        else -> clipsSort to clipsFilters
                    }
                    SortRow(
                        sortText = sortText,
                        filtersText = filtersText,
                        sortIcon = painterResource(R.drawable.baseline_sort_black_24),
                        onClick = { onSortClick() },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                beyondViewportPageCount = tabs.size,
                // The dropdown selector replaces the tab row for users who opted
                // out of tabs: switching stays programmatic, no swipe gesture.
                userScrollEnabled = useTabs,
            ) { page ->
                val atTop = { atTop: Boolean ->
                    if (tabs.getOrNull(currentTabIndex) == tabs.getOrNull(page)) {
                        headerState.atTop = atTop
                    }
                }
                val integrityFailed = {
                    activity.getNewIntegrityToken("refresh", childFragmentManager)
                }
                when (tabs.getOrNull(page)) {
                    "0" -> {
                        val refreshTick by videosRefresh.collectAsState()
                        val scrollTick by videosScrollTop.collectAsState()
                        val positions by videosViewModel.positions.collectAsState(initial = null)
                        val bookmarks by videosViewModel.bookmarks.collectAsState(initial = emptyList())
                        val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
                        VideosTab(
                            flow = videosViewModel.flow,
                            showGame = false,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            positionFor = { positions.positionFor(it) },
                            isBookmarked = { it in bookmarkIds },
                            onDownload = ::showVideoDownloadDialog,
                            onBookmark = ::saveVideoBookmark,
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "1" -> {
                        val refreshTick by streamsRefresh.collectAsState()
                        val scrollTick by streamsScrollTop.collectAsState()
                        StreamsTab(
                            flow = streamsViewModel.flow,
                            compact = streamsCompact(followedContent = false),
                            showGame = false,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            onStreamClick = { activity.startStream(it) },
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onTagClick = ::addStreamTag,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    else -> {
                        val refreshTick by clipsRefresh.collectAsState()
                        val scrollTick by clipsScrollTop.collectAsState()
                        ClipsTab(
                            flow = clipsViewModel.flow,
                            showGame = false,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            onDownload = ::showClipDownloadDialog,
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                }
            }
        }
    }

    /**
     * Dropdown tab chooser is [com.github.andreyasadchy.xtra.ui.common.TabDropdown];
     * only the pager stays here.
     */


    private fun onFollowClick() {
        val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        viewModel.isFollowing.value?.let {
            if (it) {
                requireContext().getAlertDialogBuilder()
                    .setMessage(getString(R.string.unfollow_channel, args.gameName))
                    .setNegativeButton(getString(R.string.no), null)
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        viewModel.deleteFollowGame(
                            args.gameId,
                            setting,
                            TwitchApiHelper.getGQLHeaders(requireContext(), true),
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                        )
                    }
                    .show()
            } else {
                viewModel.saveFollowGame(
                    args.gameId,
                    args.gameSlug,
                    args.gameName,
                    setting,
                    requireContext().filesDir.path,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    TwitchApiHelper.getHelixHeaders(requireContext()),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
        }
    }

    private fun onLoginClick(isLoggedIn: Boolean, activity: MainActivity) {
        if (isLoggedIn) {
            activity.getAlertDialogBuilder().apply {
                setTitle(getString(R.string.logout_title))
                requireContext().tokenPrefs().getString(C.USERNAME, null)?.let { setMessage(getString(R.string.logout_msg, it)) }
                setNegativeButton(getString(R.string.no), null)
                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java)) }
            }.show()
        } else {
            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
        }
    }

    private fun openTag(tag: Tag) {
        findNavController().navigate(
            GamesFragmentDirections.actionGlobalGamesFragment(
                tagIds = listOfNotNull(tag.id).toTypedArray(),
                tagNames = listOfNotNull(tag.name).toTypedArray(),
            )
        )
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

    private fun showVideoDownloadDialog(video: Video) {
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

    private fun saveVideoBookmark(video: Video) {
        videosViewModel.saveBookmark(
            requireContext().filesDir.path,
            video,
            TwitchApiHelper.getGQLHeaders(requireContext()),
            TwitchApiHelper.getHelixHeaders(requireContext()),
        )
    }

    private fun showClipDownloadDialog(clip: Clip) {
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

    private fun onSortClick() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "0" -> VideosSortDialog.newInstance(
                    sort = videosViewModel.sort,
                    period = videosViewModel.period,
                    type = videosViewModel.type,
                    languages = videosViewModel.languages,
                    saved = args.gameId?.let { videosViewModel.getGameSort(it) } != null,
                    tab = "videos",
                    context = "game",
                    hasId = !args.gameId.isNullOrBlank(),
                ).show(childFragmentManager, null)
                "1" -> StreamsSortDialog.newInstance(
                    sort = streamsViewModel.sort,
                    tags = streamsViewModel.tags,
                    languages = streamsViewModel.languages,
                    saved = args.gameId?.let { streamsViewModel.getGameSort(it) } != null,
                    showSaveSort = !args.gameId.isNullOrBlank(),
                ).show(childFragmentManager, null)
                else -> VideosSortDialog.newInstance(
                    sort = VideosSortDialog.SORT_VIEWS,
                    period = clipsViewModel.period,
                    languages = clipsViewModel.languages,
                    saved = args.gameId?.let { clipsViewModel.getGameSort(it) } != null,
                    tab = "clips",
                    context = "game",
                    hasId = !args.gameId.isNullOrBlank(),
                ).show(childFragmentManager, null)
            }
        }
    }

    private fun addStreamTag(tag: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tags = streamsViewModel.tags.plus(tag).sortedArray()
            streamsViewModel.setFilter(streamsViewModel.sort, tags, streamsViewModel.languages)
            streamsViewModel.filtersText.value = buildString {
                if (streamsViewModel.tags.isNotEmpty()) {
                    append(
                        resources.getQuantityString(
                            R.plurals.tags,
                            streamsViewModel.tags.size,
                            streamsViewModel.tags.joinToString()
                        )
                    )
                }
                if (streamsViewModel.languages.isNotEmpty()) {
                    if (isNotEmpty()) {
                        append(". ")
                    }
                    append(
                        resources.getQuantityString(
                            R.plurals.languages,
                            streamsViewModel.languages.size,
                            streamsViewModel.languages.joinToString()
                        )
                    )
                }
            }
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, tags: Array<String>, languages: Array<String>, changed: Boolean, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (changed) {
                streamsViewModel.setFilter(sort, tags, languages)
                streamsViewModel.sortText.value = getString(R.string.sort_by, sortText)
                streamsViewModel.filtersText.value = if (streamsViewModel.tags.isNotEmpty() || streamsViewModel.languages.isNotEmpty()) {
                    buildString {
                        if (streamsViewModel.tags.isNotEmpty()) {
                            append(
                                resources.getQuantityString(
                                    R.plurals.tags,
                                    streamsViewModel.tags.size,
                                    streamsViewModel.tags.joinToString()
                                )
                            )
                        }
                        if (streamsViewModel.languages.isNotEmpty()) {
                            if (isNotEmpty()) {
                                append(". ")
                            }
                            append(
                                resources.getQuantityString(
                                    R.plurals.languages,
                                    streamsViewModel.languages.size,
                                    streamsViewModel.languages.joinToString()
                                )
                            )
                        }
                    }
                } else null
            }
            if (saveFilters && (tags.isNotEmpty() || languages.isNotEmpty())) {
                streamsViewModel.saveFilters(
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
                    val item = streamsViewModel.getGameSort(id)?.apply {
                        streamSort = sort
                        streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
                        streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    } ?: GameSort(
                        id = id,
                        streamSort = sort,
                        streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                        streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    )
                    streamsViewModel.saveGameSort(item)
                }
            }
            if (saveDefault) {
                val item = streamsViewModel.getGameSort("default")?.apply {
                    streamSort = sort
                    streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
                    streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                } ?: GameSort(
                    id = "default",
                    streamSort = sort,
                    streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                    streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                )
                streamsViewModel.saveGameSort(item)
            }
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "0" -> {
                    if (changed) {
                        videosViewModel.setFilter(sort, period, type, languages)
                        videosViewModel.sortText.value = getString(R.string.sort_and_type, sortText, typeText)
                        videosViewModel.filtersText.value = if (languages.isNotEmpty()) {
                            resources.getQuantityString(R.plurals.languages, languages.size, languages.joinToString())
                        } else null
                    }
                    if (saveSort) {
                        args.gameId?.let { id ->
                            val item = videosViewModel.getGameSort(id)?.apply {
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
                            videosViewModel.saveGameSort(item)
                        }
                    }
                    if (saveDefault) {
                        val item = videosViewModel.getGameSort("default")?.apply {
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
                        videosViewModel.saveGameSort(item)
                    }
                }
                else -> {
                    if (changed) {
                        clipsViewModel.setFilter(period, languages)
                        clipsViewModel.sortText.value = getString(R.string.sort_and_period, sortText, periodText)
                        clipsViewModel.filtersText.value = if (languages.isNotEmpty()) {
                            resources.getQuantityString(R.plurals.languages, languages.size, languages.joinToString())
                        } else null
                    }
                    if (saveSort) {
                        args.gameId?.let { id ->
                            val item = clipsViewModel.getGameSort(id)?.apply {
                                clipPeriod = period
                                clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                            } ?: GameSort(
                                id = id,
                                clipPeriod = period,
                                clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                            )
                            clipsViewModel.saveGameSort(item)
                        }
                    }
                    if (saveDefault) {
                        val item = clipsViewModel.getGameSort("default")?.apply {
                            clipPeriod = period
                            clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        } ?: GameSort(
                            id = "default",
                            clipPeriod = period,
                            clipLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                        )
                        clipsViewModel.saveGameSort(item)
                    }
                }
            }
        }
    }

    override fun deleteSavedSort() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "0" -> args.gameId?.let { videosViewModel.getGameSort(it) }?.let { videosViewModel.deleteGameSort(it) }
                "1" -> args.gameId?.let { streamsViewModel.getGameSort(it) }?.let { streamsViewModel.deleteGameSort(it) }
                else -> args.gameId?.let { clipsViewModel.getGameSort(it) }?.let { clipsViewModel.deleteGameSort(it) }
            }
        }
    }

    private fun initializeStreamsTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (streamsViewModel.filter.value == null) {
                val sortValues = args.gameId?.let { streamsViewModel.getGameSort(it) } ?: streamsViewModel.getGameSort("default")
                streamsViewModel.setFilter(
                    sort = sortValues?.streamSort,
                    tags = args.tags ?: sortValues?.streamTags?.split(',')?.toTypedArray(),
                    languages = args.languages ?: sortValues?.streamLanguages?.split(',')?.toTypedArray(),
                )
                streamsViewModel.sortText.value = getString(
                    R.string.sort_by,
                    getString(
                        when (streamsViewModel.sort) {
                            StreamsSortDialog.SORT_VIEWERS -> R.string.viewers_high
                            StreamsSortDialog.SORT_VIEWERS_ASC -> R.string.viewers_low
                            StreamsSortDialog.RECENT -> R.string.recent
                            else -> R.string.viewers_high
                        }
                    )
                )
                streamsViewModel.filtersText.value = if (streamsViewModel.tags.isNotEmpty() || streamsViewModel.languages.isNotEmpty()) {
                    buildString {
                        if (streamsViewModel.tags.isNotEmpty()) {
                            append(
                                resources.getQuantityString(
                                    R.plurals.tags,
                                    streamsViewModel.tags.size,
                                    streamsViewModel.tags.joinToString()
                                )
                            )
                        }
                        if (streamsViewModel.languages.isNotEmpty()) {
                            if (isNotEmpty()) {
                                append(". ")
                            }
                            append(
                                resources.getQuantityString(
                                    R.plurals.languages,
                                    streamsViewModel.languages.size,
                                    streamsViewModel.languages.joinToString()
                                )
                            )
                        }
                    }
                } else null
            }
        }
    }

    private fun initializeVideosTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (videosViewModel.filter.value == null) {
                val sortValues = args.gameId?.let { videosViewModel.getGameSort(it) } ?: videosViewModel.getGameSort("default")
                videosViewModel.setFilter(
                    sort = sortValues?.videoSort,
                    period = if (!TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()) {
                        sortValues?.videoPeriod
                    } else null,
                    type = sortValues?.videoType,
                    languages = sortValues?.videoLanguages?.split(',')?.toTypedArray(),
                )
                videosViewModel.sortText.value = getString(
                    R.string.sort_and_type,
                    getString(
                        when (videosViewModel.sort) {
                            VideosSortDialog.SORT_TIME -> R.string.upload_date
                            VideosSortDialog.SORT_VIEWS -> R.string.view_count
                            else -> R.string.view_count
                        }
                    ),
                    getString(
                        when (videosViewModel.type) {
                            VideosSortDialog.VIDEO_TYPE_ARCHIVE -> R.string.video_type_archive
                            VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> R.string.video_type_highlight
                            VideosSortDialog.VIDEO_TYPE_UPLOAD -> R.string.video_type_upload
                            else -> R.string.all
                        }
                    )
                )
                videosViewModel.filtersText.value = if (videosViewModel.languages.isNotEmpty()) {
                    resources.getQuantityString(R.plurals.languages, videosViewModel.languages.size, videosViewModel.languages.joinToString())
                } else null
            }
        }
    }

    private fun initializeClipsTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (clipsViewModel.filter.value == null) {
                val sortValues = args.gameId?.let { clipsViewModel.getGameSort(it) } ?: clipsViewModel.getGameSort("default")
                clipsViewModel.setFilter(
                    period = sortValues?.clipPeriod,
                    languages = sortValues?.clipLanguages?.split(',')?.toTypedArray(),
                )
                clipsViewModel.sortText.value = getString(
                    R.string.sort_and_period,
                    getString(R.string.view_count),
                    getString(
                        when (clipsViewModel.period) {
                            VideosSortDialog.PERIOD_DAY -> R.string.today
                            VideosSortDialog.PERIOD_WEEK -> R.string.this_week
                            VideosSortDialog.PERIOD_MONTH -> R.string.this_month
                            VideosSortDialog.PERIOD_ALL -> R.string.all_time
                            else -> R.string.this_week
                        }
                    )
                )
                clipsViewModel.filtersText.value = if (clipsViewModel.languages.isNotEmpty()) {
                    resources.getQuantityString(R.plurals.languages, clipsViewModel.languages.size, clipsViewModel.languages.joinToString())
                } else null
            }
        }
    }

    override fun scrollToTop() {
        headerState.offsetPx = 0f
        when (tabs.getOrNull(currentTabIndex)) {
            "0" -> videosScrollTop.value++
            "1" -> streamsScrollTop.value++
            else -> clipsScrollTop.value++
        }
    }

    override fun onNetworkRestored() {
        viewModel.loadGame(
            TwitchApiHelper.getGQLHeaders(requireContext()),
            TwitchApiHelper.getHelixHeaders(requireContext()),
            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
        videosRefresh.value++
        streamsRefresh.value++
        clipsRefresh.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                viewModel.loadGame(
                    TwitchApiHelper.getGQLHeaders(requireContext()),
                    TwitchApiHelper.getHelixHeaders(requireContext()),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
                val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
                if (setting < 2) {
                    viewModel.isFollowingGame(
                        args.gameId,
                        setting,
                        TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    )
                }
                videosRefresh.value++
                streamsRefresh.value++
                clipsRefresh.value++
            }
            "follow" -> {
                viewModel.saveFollowGame(
                    args.gameId,
                    args.gameSlug,
                    args.gameName,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    requireContext().filesDir.path,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    TwitchApiHelper.getHelixHeaders(requireContext()),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
            "unfollow" -> {
                viewModel.deleteFollowGame(
                    args.gameId,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
        }
    }
}
