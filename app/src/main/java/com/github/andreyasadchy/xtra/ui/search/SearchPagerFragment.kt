package com.github.andreyasadchy.xtra.ui.search

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.ChannelsTab
import com.github.andreyasadchy.xtra.ui.common.GamesTab
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.StreamsTab
import com.github.andreyasadchy.xtra.ui.common.VideosTab
import com.github.andreyasadchy.xtra.ui.common.positionFor
import com.github.andreyasadchy.xtra.ui.common.streamsCompact
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerViewModel.Companion.SearchPagerViewModelFactory
import com.github.andreyasadchy.xtra.ui.search.channels.ChannelSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.channels.ChannelSearchViewModel.Companion.ChannelSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.search.games.GameSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.games.GameSearchViewModel.Companion.GameSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.search.streams.StreamSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.streams.StreamSearchViewModel.Companion.StreamSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.search.videos.VideoSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.videos.VideoSearchViewModel.Companion.VideoSearchViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Search screen as one Compose scaffold: a search field, the configured tabs and
 * a pager over the four shared list tabs. It used to be a ViewPager2 host over
 * four child search fragments; the host now owns their ViewModels, so a query
 * applies to the tab you are on and follows you when you switch tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SearchPagerFragment : BaseNetworkFragment(), IntegrityDialog.Listener {

    private val viewModel: SearchPagerViewModel by viewModels { SearchPagerViewModelFactory }
    private val videosViewModel by lazy { ViewModelProvider(this, VideoSearchViewModelFactory)["videos", VideoSearchViewModel::class.java] }
    private val streamsViewModel by lazy { ViewModelProvider(this, StreamSearchViewModelFactory)["streams", StreamSearchViewModel::class.java] }
    private val channelsViewModel by lazy { ViewModelProvider(this, ChannelSearchViewModelFactory)["channels", ChannelSearchViewModel::class.java] }
    private val gamesViewModel by lazy { ViewModelProvider(this, GameSearchViewModelFactory)["games", GameSearchViewModel::class.java] }

    private val refreshSignal = MutableStateFlow(0)
    private var tabs: List<String> = listOf("2")
    private var initialTabIndex: Int = 0
    private var currentTabIndex by mutableIntStateOf(0)
    private var searchQuery by mutableStateOf("")
    private var searchedQuery: String? = null
    private var showUserResultDialog by mutableStateOf(false)
    private var userResult: Pair<Int?, String?>? = null

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
                        SearchScreen()
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

    private fun computeTabs(): List<String> {
        val tabList = requireContext().prefs().getString(C.UI_SEARCH_TABS, null).let { tabPref ->
            val defaultTabs = C.DEFAULT_SEARCH_TABS.split(',')
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
            if (enabled) {
                key
            } else {
                null
            }
        }
    }

    private fun computeInitialTab(): Int {
        val tabList = requireContext().prefs().getString(C.UI_SEARCH_TABS, null)?.split(',') ?: C.DEFAULT_SEARCH_TABS.split(',')
        val defaultItem = tabList.find { it.split(':')[1] != "0" }?.split(':')[0] ?: "2"
        return tabs.indexOf(defaultItem).takeIf { it != -1 } ?: tabs.indexOf("2").takeIf { it != -1 } ?: 0
    }

    /** Applies [query] to the ViewModel behind [tab], saving it like the old children did. */
    private fun search(tab: String?, query: String) {
        val store = requireContext().prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)
        when (tab) {
            "0" -> {
                videosViewModel.setQuery(query)
                if (store) videosViewModel.saveRecentSearch(query)
            }
            "1" -> {
                streamsViewModel.setQuery(query)
                if (store) streamsViewModel.saveRecentSearch(query)
            }
            "2" -> {
                channelsViewModel.setQuery(query)
                if (store) channelsViewModel.saveRecentSearch(query)
            }
            "3" -> {
                gamesViewModel.setQuery(query)
                if (store) gamesViewModel.saveRecentSearch(query)
            }
        }
    }

    @Composable
    private fun SearchScreen() {
        val activity = requireActivity() as MainActivity
        val pagerState = rememberPagerState(initialPage = initialTabIndex) { tabs.size }
        val scope = rememberCoroutineScope()
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        val bottomInset = xtraBottomInset(activity)
        val refreshTick by refreshSignal.collectAsState()
        val keyboard = LocalSoftwareKeyboardController.current
        val searchFocus = remember { FocusRequester() }
        var overflowExpanded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                val previous = currentTabIndex
                currentTabIndex = page
                // Carrying the query to the tab you just switched to, as the old
                // onPageSelected callback did.
                if (previous != page) search(tabs.getOrNull(page), searchQuery)
            }
        }
        LaunchedEffect(searchQuery) {
            if (searchQuery == searchedQuery) return@LaunchedEffect
            if (searchQuery.isEmpty()) {
                searchedQuery = ""
                search(tabs.getOrNull(currentTabIndex), "")
            } else {
                delay(750.milliseconds)
                searchedQuery = searchQuery
                search(tabs.getOrNull(currentTabIndex), searchQuery)
            }
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        searchedQuery = searchQuery
                                        search(tabs.getOrNull(currentTabIndex), searchQuery)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { findNavController().navigateUp() }) {
                                Icon(painterResource(R.drawable.baseline_arrow_back_black_24), contentDescription = null)
                            }
                        },
                        actions = {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(painterResource(R.drawable.baseline_more_vert_black_24), contentDescription = null)
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.search_user)) },
                                    onClick = {
                                        overflowExpanded = false
                                        showUserResultDialog = true
                                    },
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                    val tabTitles = tabs.map { tabId ->
                        stringResource(
                            when (tabId) {
                                "0" -> R.string.videos
                                "1" -> R.string.streams
                                "2" -> R.string.channels
                                else -> R.string.games
                            }
                        )
                    }
                    if (tabTitles.size > 1) {
                        SecondaryTabRow(
                            currentTabIndex,
                            Modifier,
                            TabRowDefaults.primaryContainerColor,
                            TabRowDefaults.primaryContentColor,
                            @Composable {
                                if (currentTabIndex < tabTitles.size) {
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
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                beyondViewportPageCount = tabs.size,
            ) { page ->
                val integrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) }
                when (tabs.getOrNull(page)) {
                    "0" -> SearchTab(
                        query = videosViewModel.query,
                        recentSearches = videosViewModel.recentSearches,
                        onDeleteRecent = videosViewModel::deleteRecentSearch,
                    ) {
                        val positions by videosViewModel.positions.collectAsState(initial = null)
                        val bookmarks by videosViewModel.bookmarks.collectAsState(initial = emptyList())
                        val bookmarkIds = remember(bookmarks) { bookmarks.map { it.videoId }.toSet() }
                        VideosTab(
                            flow = videosViewModel.flow,
                            showGame = true,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = 0,
                            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                            positionFor = { positions.positionFor(it) },
                            isBookmarked = { it in bookmarkIds },
                            onDownload = ::showVideoDownloadDialog,
                            onBookmark = ::saveVideoBookmark,
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "1" -> SearchTab(
                        query = streamsViewModel.query,
                        recentSearches = streamsViewModel.recentSearches,
                        onDeleteRecent = streamsViewModel::deleteRecentSearch,
                    ) {
                        StreamsTab(
                            flow = streamsViewModel.flow,
                            // Search results are not "followed" content.
                            compact = streamsCompact(followedContent = false),
                            showGame = true,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = 0,
                            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                            onStreamClick = { activity.startStream(it) },
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onTagClick = ::openTag,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "2" -> SearchTab(
                        query = channelsViewModel.query,
                        recentSearches = channelsViewModel.recentSearches,
                        onDeleteRecent = channelsViewModel::deleteRecentSearch,
                    ) {
                        val context = LocalContext.current
                        ChannelsTab(
                            flow = channelsViewModel.flow,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = 0,
                            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                            detailsFor = { listOfNotNull(context.collectionCount(it.followerCount, R.plurals.followers)) },
                            labelsFor = { user -> listOfNotNull(context.getString(R.string.live).takeIf { user.isLive == true }) },
                            onClick = ::openChannel,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    else -> SearchTab(
                        query = gamesViewModel.query,
                        recentSearches = gamesViewModel.recentSearches,
                        onDeleteRecent = gamesViewModel::deleteRecentSearch,
                    ) {
                        GamesTab(
                            flow = gamesViewModel.flow,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = 0,
                            modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                            diskCache = true,
                            onTagClick = ::openTag,
                            onClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                }
            }
        }
        if (showUserResultDialog) {
            UserResultDialog(onDismiss = { showUserResultDialog = false })
        }
    }

    /**
     * A tab either shows its recent searches (empty query) or its list. The
     * recent-search click writes into the search field, which drives the search.
     */
    @Composable
    private fun SearchTab(
        query: StateFlow<String>,
        recentSearches: Flow<List<RecentSearch>>,
        onDeleteRecent: (RecentSearch) -> Unit,
        list: @Composable () -> Unit,
    ) {
        val currentQuery by query.collectAsState()
        if (currentQuery.isBlank() && requireContext().prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)) {
            val recents by recentSearches.collectAsState(initial = emptyList())
            RecentSearchList(
                searches = recents,
                onSelect = { applyQuery(it) },
                onDelete = onDeleteRecent,
            )
        } else {
            list()
        }
    }

    /** Setting the field and searching straight away, as `setQuery(.., true)` did. */
    private fun applyQuery(query: String) {
        searchQuery = query
        searchedQuery = query
        search(tabs.getOrNull(currentTabIndex), query)
    }

    @Composable
    private fun UserResultDialog(onDismiss: () -> Unit) {
        var input by rememberSaveable { mutableStateOf("") }
        var byId by rememberSaveable { mutableStateOf(false) }
        var confirm by remember { mutableStateOf<Pair<String?, String?>?>(null) }
        val result by viewModel.userResult.collectAsState()
        LaunchedEffect(result) {
            result?.let {
                if (!it.first.isNullOrBlank()) {
                    confirm = it
                } else {
                    viewUserResult()
                }
                viewModel.userResult.value = null
            }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.search_user)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = byId, onClick = { byId = true })
                        Text(stringResource(R.string.user_id))
                        RadioButton(selected = !byId, onClick = { byId = false })
                        Text(stringResource(R.string.user_login))
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            userResult = Pair(if (byId) 0 else 1, input)
                            viewUserResult()
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.view_profile))
                    }
                    TextButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                val checkedId = if (byId) 0 else 1
                                userResult = Pair(checkedId, input)
                                viewModel.loadUserResult(
                                    checkedId = checkedId,
                                    result = input,
                                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                                )
                            }
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        confirm?.let { (title, message) ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(title.orEmpty()) },
                text = { Text(message.orEmpty()) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = null
                            viewUserResult()
                        }
                    ) {
                        Text(stringResource(R.string.view_profile))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
    }

    private fun viewUserResult() {
        userResult?.let {
            when (it.first) {
                0 -> findNavController().navigate(
                    ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                        channelId = it.second
                    )
                )
                1 -> findNavController().navigate(
                    ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                        channelLogin = it.second
                    )
                )
                else -> {}
            }
        }
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

    private fun openChannel(stream: Stream) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = stream.channelId,
                channelLogin = stream.channelLogin,
                channelName = stream.channelName,
                channelImage = stream.channelImage,
                streamId = stream.id,
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

    private fun openGame(game: Game) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    boxArt = game.boxArt,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    boxArt = game.boxArt,
                )
            }
        )
    }

    private fun openGame(stream: Stream) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = stream.gameId,
                    gameSlug = stream.gameSlug,
                    gameName = stream.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = stream.gameId,
                    gameSlug = stream.gameSlug,
                    gameName = stream.gameName,
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

    private fun openTag(tag: String) {
        findNavController().navigate(
            TopStreamsFragmentDirections.actionGlobalTopFragment(
                tags = arrayOf(tag)
            )
        )
    }

    private fun openTag(tag: Tag) {
        findNavController().navigate(
            GamesFragmentDirections.actionGlobalGamesFragment(
                tagIds = listOfNotNull(tag.id).toTypedArray(),
                tagNames = listOfNotNull(tag.name).toTypedArray(),
            )
        )
    }

    override fun initialize() {
        // No adapter: the tab composables collect their view model flows.
    }

    override fun onNetworkRestored() {
        refreshSignal.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                refreshSignal.value++
            }
        }
    }
}
