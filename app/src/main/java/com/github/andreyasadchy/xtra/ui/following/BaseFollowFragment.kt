package com.github.andreyasadchy.xtra.ui.following

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.collectionFollowLabels
import com.github.andreyasadchy.xtra.ui.common.ChannelsTab
import com.github.andreyasadchy.xtra.ui.common.CollapsingHeaderState
import com.github.andreyasadchy.xtra.ui.common.GamesTab
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.SortRow
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
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsSortDialog
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsViewModel
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsViewModel.Companion.FollowedChannelsViewModelFactory
import com.github.andreyasadchy.xtra.ui.following.games.FollowedGamesViewModel
import com.github.andreyasadchy.xtra.ui.following.games.FollowedGamesViewModel.Companion.FollowedGamesViewModelFactory
import com.github.andreyasadchy.xtra.ui.following.streams.FollowedStreamsViewModel
import com.github.andreyasadchy.xtra.ui.following.streams.FollowedStreamsViewModel.Companion.FollowedStreamsViewModelFactory
import com.github.andreyasadchy.xtra.ui.following.videos.FollowedVideosViewModel
import com.github.andreyasadchy.xtra.ui.following.videos.FollowedVideosViewModel.Companion.FollowedVideosViewModelFactory
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.formatChatDate
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Shared host for the two Following screens as a full-Compose scaffold: app bar,
 * tab selector, sort row and one pager over the four shared list tabs. Hosts own
 * the ViewModels, signals, navigation and dialogs; the list bodies live in
 * `:core:ui` exactly once.
 *
 * Subclasses only pick the tab selector: [FollowPagerFragment] renders a tab row,
 * [FollowMediaFragment] (the "use tabs for the Following page" opt-out) renders a
 * dropdown without swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
abstract class BaseFollowFragment : PagedListFragment(), Scrollable, FollowedChannelsSortDialog.OnFilter, VideosSortDialog.OnFilter {

    /** Tab row when true, dropdown chooser (no swipe) when false. */
    protected abstract val useTabs: Boolean

    private val channelsViewModel by lazy { ViewModelProvider(this, FollowedChannelsViewModelFactory)["channels", FollowedChannelsViewModel::class.java] }
    private val gamesViewModel by lazy { ViewModelProvider(this, FollowedGamesViewModelFactory)["games", FollowedGamesViewModel::class.java] }
    private val streamsViewModel by lazy { ViewModelProvider(this, FollowedStreamsViewModelFactory)["streams", FollowedStreamsViewModel::class.java] }
    private val videosViewModel by lazy { ViewModelProvider(this, FollowedVideosViewModelFactory)["videos", FollowedVideosViewModel::class.java] }

    private val channelsRefresh = MutableStateFlow(0)
    private val channelsScrollTop = MutableStateFlow(0)
    private val gamesRefresh = MutableStateFlow(0)
    private val gamesScrollTop = MutableStateFlow(0)
    private val streamsRefresh = MutableStateFlow(0)
    private val streamsScrollTop = MutableStateFlow(0)
    private val videosRefresh = MutableStateFlow(0)
    private val videosScrollTop = MutableStateFlow(0)

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
                        FollowScreen()
                    }
                }
            }
        }
    }

    private fun computeTabs(): List<String> {
        val showVideosTab = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank()
        val tabList = requireContext().prefs().getString(C.UI_FOLLOWING_TABS, null).let { tabPref ->
            val defaultTabs = C.DEFAULT_FOLLOWING_TABS.split(',')
            if (tabPref != null) {
                val list = tabPref.split(',').filter { item ->
                    defaultTabs.find { it.first() == item.first() } != null
                }.toMutableList()
                defaultTabs.forEachIndexed { index, item ->
                    if (list.find { it.first() == item.first() } == null) {
                        list.add(index, item)
                    }
                }
                list
            } else defaultTabs
        }
        return tabList.mapNotNull {
            val split = it.split(':')
            val key = split[0]
            val enabled = split[2] != "0"
            if (enabled && (key != "2" || showVideosTab)) {
                key
            } else {
                null
            }
        }
    }

    private fun computeInitialTab(): Int {
        val tabList = requireContext().prefs().getString(C.UI_FOLLOWING_TABS, null)?.split(',') ?: C.DEFAULT_FOLLOWING_TABS.split(',')
        val defaultItem = tabList.find { it.split(':')[1] != "0" }?.split(':')?.get(0) ?: "1"
        return tabs.indexOf(defaultItem).takeIf { it != -1 } ?: tabs.indexOf("1").takeIf { it != -1 } ?: 0
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (channelsViewModel.filter.value == null) {
                val sortValues = channelsViewModel.getChannelSort("followed_channels")
                channelsViewModel.setFilter(
                    sort = sortValues?.videoSort,
                    order = sortValues?.videoType,
                )
                channelsViewModel.sortText.value = getString(
                    R.string.sort_and_order,
                    getString(
                        when (channelsViewModel.sort) {
                            FollowedChannelsSortDialog.SORT_FOLLOWED_AT -> R.string.time_followed
                            FollowedChannelsSortDialog.SORT_ALPHABETICALLY -> R.string.alphabetically
                            FollowedChannelsSortDialog.SORT_LAST_BROADCAST -> R.string.last_broadcast
                            else -> R.string.last_broadcast
                        }
                    ),
                    getString(
                        when (channelsViewModel.order) {
                            FollowedChannelsSortDialog.ORDER_DESC -> R.string.descending
                            FollowedChannelsSortDialog.ORDER_ASC -> R.string.ascending
                            else -> R.string.descending
                        }
                    )
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (videosViewModel.filter.value == null) {
                val sortValues = videosViewModel.getChannelSort("followed_videos")
                videosViewModel.setFilter(
                    sort = sortValues?.videoSort,
                    type = sortValues?.videoType,
                )
                videosViewModel.sortText.value = getString(
                    R.string.sort_and_type,
                    getString(
                        when (videosViewModel.sort) {
                            VideosSortDialog.SORT_TIME -> R.string.upload_date
                            VideosSortDialog.SORT_VIEWS -> R.string.view_count
                            else -> R.string.upload_date
                        }
                    ),
                    getString(R.string.all)
                )
            }
        }
    }

    @Composable
    private fun FollowScreen() {
        val pagerState = rememberPagerState(initialPage = initialTabIndex) { tabs.size }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { currentTabIndex = it }
        }
        val collapseConnection = rememberCollapseConnection(headerState)
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        val tabModifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
        val context = LocalContext.current
        val activity = requireActivity() as MainActivity
        val isLoggedIn = remember {
            !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        }
        val liftOptOut = remember { !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true) }
        val bottomInset = xtraBottomInset(activity)
        Scaffold(
            modifier = Modifier.nestedScroll(collapseConnection),
            topBar = {
                Column {
                    XtraTopBar(
                        title = stringResource(R.string.following),
                        isLoggedIn = isLoggedIn,
                        liftOptOut = liftOptOut,
                        onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                        onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                        onLogin = { onLoginClick(isLoggedIn, activity) },
                        up = { findNavController().navigateUp() },
                    )
                    val tabTitles = tabs.map { tabId ->
                        stringResource(
                            when (tabId) {
                                "0" -> R.string.games
                                "1" -> R.string.live
                                "2" -> R.string.videos
                                else -> R.string.channels
                            }
                        )
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
                    // Only the videos and channels tabs have a sort bar; the games
                    // and live tabs never did.
                    when (tabs.getOrNull(currentTabIndex)) {
                        "2" -> {
                            val sortText by videosViewModel.sortText.collectAsState()
                            SortRow(
                                sortText = sortText,
                                filtersText = null,
                                sortIcon = painterResource(R.drawable.baseline_sort_black_24),
                                onClick = { onSortClick() },
                            )
                        }
                        "3" -> {
                            val sortText by channelsViewModel.sortText.collectAsState()
                            SortRow(
                                sortText = sortText,
                                filtersText = null,
                                sortIcon = painterResource(R.drawable.baseline_sort_black_24),
                                onClick = { onSortClick() },
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                beyondViewportPageCount = tabs.size,
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
                        val refreshTick by gamesRefresh.collectAsState()
                        val scrollTick by gamesScrollTop.collectAsState()
                        GamesTab(
                            flow = gamesViewModel.flow,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            followLabels = { context.collectionFollowLabels(it.accountFollow, it.localFollow) },
                            onTagClick = ::openTag,
                            onClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "1" -> {
                        val refreshTick by streamsRefresh.collectAsState()
                        val scrollTick by streamsScrollTop.collectAsState()
                        StreamsTab(
                            flow = streamsViewModel.flow,
                            compact = streamsCompact(followedContent = true),
                            showGame = true,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            onStreamClick = { activity.startStream(it) },
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onTagClick = ::openTag,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "2" -> {
                        val refreshTick by videosRefresh.collectAsState()
                        val scrollTick by videosScrollTop.collectAsState()
                        val positions by videosViewModel.positions.collectAsState(initial = null)
                        val bookmarks by videosViewModel.bookmarks.collectAsState(initial = emptyList())
                        val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
                        VideosTab(
                            flow = videosViewModel.flow,
                            showGame = true,
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
                    else -> {
                        val refreshTick by channelsRefresh.collectAsState()
                        val scrollTick by channelsScrollTop.collectAsState()
                        fun date(value: String?, label: Int): String? = value?.let {
                            Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }?.let { time ->
                                context.getString(label, formatChatDate(time))
                            }
                        }
                        ChannelsTab(
                            flow = channelsViewModel.flow,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            detailsFor = {
                                listOfNotNull(
                                    date(it.lastBroadcast, R.string.last_broadcast_date),
                                    date(it.followedAt, R.string.followed_at),
                                )
                            },
                            labelsFor = { context.collectionFollowLabels(it.accountFollow, it.localFollow) },
                            onClick = ::openChannel,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                }
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

    private fun onSortClick() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "2" -> VideosSortDialog.newInstance(
                    sort = videosViewModel.sort,
                    period = videosViewModel.period,
                    type = videosViewModel.type,
                    tab = "videos",
                    context = "followed",
                    hasId = false,
                ).show(childFragmentManager, null)
                else -> FollowedChannelsSortDialog.newInstance(
                    sort = channelsViewModel.sort,
                    order = channelsViewModel.order,
                ).show(childFragmentManager, null)
            }
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

    private fun openTag(tag: String) {
        findNavController().navigate(
            TopStreamsFragmentDirections.actionGlobalTopFragment(
                tags = arrayOf(tag)
            )
        )
    }

    private fun openGame(game: Game) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    updateLocal = game.localFollow,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    updateLocal = game.localFollow,
                )
            }
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

    override fun onChange(sort: String, sortText: CharSequence, order: String, orderText: CharSequence, changed: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (changed) {
                channelsViewModel.setFilter(sort, order)
                channelsViewModel.sortText.value = getString(R.string.sort_and_order, sortText, orderText)
            }
            if (saveDefault) {
                val item = channelsViewModel.getChannelSort("followed_channels")?.apply {
                    videoSort = sort
                    videoType = order
                } ?: ChannelSort(
                    id = "followed_channels",
                    videoSort = sort,
                    videoType = order
                )
                channelsViewModel.saveChannelSort(item)
            }
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (changed) {
                videosViewModel.setFilter(sort, type)
                videosViewModel.sortText.value = getString(R.string.sort_and_type, sortText, typeText)
            }
            if (saveDefault) {
                val item = videosViewModel.getChannelSort("followed_videos")?.apply {
                    videoSort = sort
                    videoType = type
                } ?: ChannelSort(
                    id = "followed_videos",
                    videoSort = sort,
                    videoType = type
                )
                videosViewModel.saveChannelSort(item)
            }
        }
    }

    override fun deleteSavedSort() {
    }

    override fun scrollToTop() {
        headerState.offsetPx = 0f
        when (tabs.getOrNull(currentTabIndex)) {
            "0" -> gamesScrollTop.value++
            "1" -> streamsScrollTop.value++
            "2" -> videosScrollTop.value++
            else -> channelsScrollTop.value++
        }
    }

    override fun onNetworkRestored() {
        channelsRefresh.value++
        gamesRefresh.value++
        streamsRefresh.value++
        videosRefresh.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                channelsRefresh.value++
                gamesRefresh.value++
                streamsRefresh.value++
                videosRefresh.value++
            }
        }
    }
}
