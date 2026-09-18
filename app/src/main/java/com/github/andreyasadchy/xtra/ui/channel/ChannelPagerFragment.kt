package com.github.andreyasadchy.xtra.ui.channel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.edit
import androidx.fragment.app.viewModels
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerViewModel.Companion.ChannelPagerViewModelFactory
import com.github.andreyasadchy.xtra.ui.channel.about.ChannelAboutFragment
import com.github.andreyasadchy.xtra.ui.channel.clips.ChannelClipsViewModel
import com.github.andreyasadchy.xtra.ui.channel.clips.ChannelClipsViewModel.Companion.ChannelClipsViewModelFactory
import com.github.andreyasadchy.xtra.ui.channel.suggestions.ChannelSuggestionsViewModel
import com.github.andreyasadchy.xtra.ui.channel.suggestions.ChannelSuggestionsViewModel.Companion.ChannelSuggestionsViewModelFactory
import com.github.andreyasadchy.xtra.ui.channel.videos.ChannelVideosViewModel
import com.github.andreyasadchy.xtra.ui.channel.videos.ChannelVideosViewModel.Companion.ChannelVideosViewModelFactory
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.ClipsTab
import com.github.andreyasadchy.xtra.ui.common.CollapsingBanner
import com.github.andreyasadchy.xtra.ui.common.CollapsingHeaderState
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.SortRow
import com.github.andreyasadchy.xtra.ui.common.StreamsTab
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.common.VideosTab
import com.github.andreyasadchy.xtra.ui.common.XtraTopBar
import com.github.andreyasadchy.xtra.ui.common.positionFor
import com.github.andreyasadchy.xtra.ui.common.rememberCollapseConnection
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Channel screen as a full-Compose scaffold: the app bar, the collapsing channel
 * banner, the tab row and the sort row live on top, with one shared
 * nested-scroll system underneath. The channel lists (suggestions, videos,
 * clips) are inlined and the View-based Chat and Compose About tabs are hosted
 * through [AndroidFragment]. This replaces the old pinned ViewPager2 header,
 * which never collapsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
class ChannelPagerFragment : BaseNetworkFragment(), Scrollable, VideosSortDialog.OnFilter, IntegrityDialog.Listener {

    private val args: ChannelPagerFragmentArgs by navArgs()
    private val viewModel: ChannelPagerViewModel by viewModels { ChannelPagerViewModelFactory }
    private val suggestionsViewModel by lazy { ViewModelProvider(this, ChannelSuggestionsViewModelFactory)["suggestions", ChannelSuggestionsViewModel::class.java] }
    private val videosViewModel by lazy { ViewModelProvider(this, ChannelVideosViewModelFactory)["videos", ChannelVideosViewModel::class.java] }
    private val clipsViewModel by lazy { ViewModelProvider(this, ChannelClipsViewModelFactory)["clips", ChannelClipsViewModel::class.java] }

    private val suggestionsRefresh = MutableStateFlow(0)
    private val suggestionsScrollTop = MutableStateFlow(0)
    private val videosRefresh = MutableStateFlow(0)
    private val videosScrollTop = MutableStateFlow(0)
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
                        ChannelScreen()
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

    @Composable
    private fun ChannelScreen() {
        val pagerState = rememberPagerState(initialPage = initialTabIndex) { tabs.size }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { currentTabIndex = it }
        }
        val collapseConnection = rememberCollapseConnection(headerState)
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        val tabModifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
        val activity = requireActivity() as MainActivity
        val isLoggedIn = remember {
            !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        }
        val followSetting = remember { requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0 }
        val liftOptOut = remember { !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true) }
        val bottomInset = xtraBottomInset(activity)

        val stream by viewModel.stream.collectAsState()
        val user by viewModel.user.collectAsState()
        val isFollowing by viewModel.isFollowing.collectAsState()
        val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
        val followResult by viewModel.follow.collectAsState()
        val notificationsResult by viewModel.notifications.collectAsState()

        LaunchedEffect(user) {
            val localUser = user
            if (args.updateLocal && localUser != null) {
                viewModel.updateLocalUser(requireContext().filesDir.path, localUser)
            }
        }
        LaunchedEffect(followResult) {
            followResult?.let { (following, errorMessage) ->
                if (!errorMessage.isNullOrBlank()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(if (following) R.string.now_following else R.string.unfollowed, channelDisplayName(stream, user)),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                viewModel.follow.value = null
            }
        }
        LaunchedEffect(notificationsResult) {
            notificationsResult?.let { (enabled, errorMessage) ->
                if (!errorMessage.isNullOrBlank()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), if (enabled) R.string.enabled_notifications else R.string.disabled_notifications, Toast.LENGTH_SHORT).show()
                }
                viewModel.notifications.value = null
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(collapseConnection),
            topBar = {
                Column {
                    XtraTopBar(
                        title = channelDisplayName(stream, user) ?: "",
                        isLoggedIn = isLoggedIn,
                        liftOptOut = liftOptOut,
                        onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                        onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                        onLogin = { onLoginClick(isLoggedIn, activity) },
                        up = { findNavController().navigateUp() },
                        actions = {
                            IconButton(onClick = { onNotificationsClick() }) {
                                Icon(
                                    painterResource(if (notificationsEnabled == true) R.drawable.baseline_notifications_black_24 else R.drawable.baseline_notifications_none_black_24),
                                    contentDescription = stringResource(if (notificationsEnabled == true) R.string.disable_notifications else R.string.enable_notifications),
                                )
                            }
                            if (followSetting < 2) {
                                IconButton(onClick = { onFollowClick() }) {
                                    Icon(
                                        painterResource(if (isFollowing == true) R.drawable.baseline_favorite_black_24 else R.drawable.baseline_favorite_border_black_24),
                                        contentDescription = stringResource(if (isFollowing == true) R.string.unfollow else R.string.follow),
                                    )
                                }
                            }
                        },
                        extraOverflow = listOf(
                            getString(R.string.share) to { onShare() },
                            getString(R.string.download) to { onDownload() },
                        ),
                    )
                    // Chat keeps the toolbar and tabs but drops the banner, as the
                    // old View header did when the chat page was selected.
                    if (tabs.getOrNull(currentTabIndex) != "3") {
                        CollapsingBanner(headerState) {
                            ChannelBannerContent(
                                stream = stream,
                                user = user,
                                fallbackName = args.channelName,
                                fallbackImage = args.channelImage,
                                onWatchLive = { onWatchLive(stream) },
                                onGameClick = ::openGame,
                            )
                        }
                    }
                    val tabTitles = tabs.map { tabId ->
                        stringResource(
                            when (tabId) {
                                "0" -> R.string.suggestions
                                "1" -> R.string.videos
                                "2" -> R.string.clips
                                "3" -> R.string.chat
                                else -> R.string.about
                            }
                        )
                    }
                    SecondaryTabRow(
                        currentTabIndex,
                        Modifier,
                        TabRowDefaults.primaryContainerColor,
                        TabRowDefaults.primaryContentColor,
                        @Composable {
                            if (currentTabIndex < tabs.size) {
                                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(currentTabIndex))
                            }
                        },
                        @Composable { HorizontalDivider() },
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = currentTabIndex == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                    when (tabs.getOrNull(currentTabIndex)) {
                        "1" -> {
                            val sortText by videosViewModel.sortText.collectAsState()
                            SortRow(
                                sortText = sortText,
                                filtersText = null,
                                sortIcon = painterResource(R.drawable.baseline_sort_black_24),
                                onClick = { onSortClick() },
                            )
                        }
                        "2" -> {
                            val sortText by clipsViewModel.sortText.collectAsState()
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
                        val refreshTick by suggestionsRefresh.collectAsState()
                        val scrollTick by suggestionsScrollTop.collectAsState()
                        StreamsTab(
                            flow = suggestionsViewModel.flow,
                            compact = true,
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
                    "1" -> {
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
                            showChannel = false,
                            positionFor = { positions.positionFor(it) },
                            isBookmarked = { it in bookmarkIds },
                            onDownload = ::showVideoDownloadDialog,
                            onBookmark = ::saveVideoBookmark,
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "2" -> {
                        val refreshTick by clipsRefresh.collectAsState()
                        val scrollTick by clipsScrollTop.collectAsState()
                        ClipsTab(
                            flow = clipsViewModel.flow,
                            showGame = true,
                            bottomInset = bottomInset,
                            portrait = portrait,
                            refreshTick = refreshTick,
                            scrollTick = scrollTick,
                            onAtTopChanged = atTop,
                            modifier = tabModifier,
                            showChannel = false,
                            onDownload = ::showClipDownloadDialog,
                            onChannelClick = ::openChannel,
                            onGameClick = ::openGame,
                            onIntegrityFailed = integrityFailed,
                        )
                    }
                    "3" -> {
                        val chatArguments = remember {
                            ChatFragment.newInstance(args.channelId, args.channelLogin, args.channelName, args.streamId).arguments ?: Bundle.EMPTY
                        }
                        AndroidFragment<ChatFragment>(modifier = Modifier.fillMaxSize(), arguments = chatArguments)
                    }
                    else -> {
                        AndroidFragment<ChannelAboutFragment>(modifier = Modifier.fillMaxSize(), arguments = arguments ?: Bundle.EMPTY)
                    }
                }
            }
        }
    }

    override fun initialize() {
        viewModel.loadStream(
            TwitchApiHelper.getGQLHeaders(requireContext()),
            TwitchApiHelper.getHelixHeaders(requireContext()),
            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
        viewModel.isFollowingChannel(
            requireContext().tokenPrefs().getString(C.USER_ID, null),
            args.channelId,
            args.channelLogin,
            requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
            TwitchApiHelper.getGQLHeaders(requireContext(), true),
            TwitchApiHelper.getHelixHeaders(requireContext()),
        )
        initializeVideosTab()
        initializeClipsTab()
    }

    private fun computeTabs(): List<String> {
        val tabList = requireContext().prefs().getString(C.UI_CHANNEL_TABS, null).let { tabPref ->
            val defaultTabs = C.DEFAULT_CHANNEL_TABS.split(',')
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
            if (enabled) key else null
        }
    }

    private fun computeInitialTab(): Int {
        val tabList = requireContext().prefs().getString(C.UI_CHANNEL_TABS, null)?.split(',') ?: C.DEFAULT_CHANNEL_TABS.split(',')
        val defaultItem = tabList.find { it.split(':')[1] != "0" }?.split(':')?.get(0) ?: "1"
        return tabs.indexOf(defaultItem).takeIf { it != -1 } ?: tabs.indexOf("1").takeIf { it != -1 } ?: 0
    }

    private fun initializeVideosTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (videosViewModel.filter.value == null) {
                val sortValues = args.channelId?.let { videosViewModel.getChannelSort(it) } ?: videosViewModel.getChannelSort("default")
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
                    getString(
                        when (videosViewModel.type) {
                            VideosSortDialog.VIDEO_TYPE_ARCHIVE -> R.string.video_type_archive
                            VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> R.string.video_type_highlight
                            VideosSortDialog.VIDEO_TYPE_UPLOAD -> R.string.video_type_upload
                            else -> R.string.all
                        }
                    ),
                )
            }
        }
    }

    private fun initializeClipsTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (clipsViewModel.filter.value == null) {
                val sortValues = args.channelId?.let { clipsViewModel.getChannelSort(it) } ?: clipsViewModel.getChannelSort("default")
                clipsViewModel.setFilter(period = sortValues?.clipPeriod)
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
                    ),
                )
            }
        }
    }

    private fun channelDisplayName(stream: Stream?, user: User?): String? {
        val name = user?.name ?: stream?.channelName ?: args.channelName
        val login = user?.login ?: stream?.channelLogin ?: args.channelLogin
        return if (login != null && !login.equals(name, true)) {
            when (requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                "0" -> "${name}(${login})"
                "1" -> name
                else -> login
            }
        } else name
    }

    private fun onWatchLive(stream: Stream?) {
        val activity = requireActivity() as MainActivity
        if (stream?.viewerCount != null) {
            activity.startStream(stream)
        } else {
            activity.startStream(
                Stream(
                    id = args.streamId,
                    channelId = args.channelId,
                    channelLogin = args.channelLogin,
                    channelName = args.channelName,
                    channelImageURL = args.channelImage,
                )
            )
        }
    }

    private fun onNotificationsClick() {
        viewModel.notificationsEnabled.value?.let { enabled ->
            if (enabled) {
                viewModel.disableNotifications(
                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                    args.channelId,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            } else {
                val activity = requireActivity() as MainActivity
                val notificationsEnabled = requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false)
                viewModel.enableNotifications(
                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                    args.channelId,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    notificationsEnabled,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
                if (!args.channelId.isNullOrBlank() && !notificationsEnabled) {
                    if (PackageManager.PERMISSION_GRANTED !=
                        androidx.core.content.ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                    viewModel.updateNotifications(
                        TwitchApiHelper.getGQLHeaders(requireContext(), true),
                        TwitchApiHelper.getHelixHeaders(requireContext()),
                    )
                    WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                        "live_notifications",
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                        PeriodicWorkRequestBuilder<com.github.andreyasadchy.xtra.ui.main.LiveNotificationWorker>(15, TimeUnit.MINUTES)
                            .setInitialDelay(1, TimeUnit.MINUTES)
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()
                            )
                            .build()
                    )
                    requireContext().prefs().edit { putBoolean(C.LIVE_NOTIFICATIONS_ENABLED, true) }
                }
            }
        }
    }

    private fun onFollowClick() {
        val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        viewModel.isFollowing.value?.let {
            if (it) {
                requireContext().getAlertDialogBuilder()
                    .setMessage(getString(R.string.unfollow_channel, channelDisplayName(viewModel.stream.value, viewModel.user.value)))
                    .setNegativeButton(getString(R.string.no), null)
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        viewModel.deleteFollowChannel(
                            requireContext().tokenPrefs().getString(C.USER_ID, null),
                            args.channelId,
                            setting,
                            TwitchApiHelper.getGQLHeaders(requireContext(), true),
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                        )
                    }
                    .show()
            } else {
                viewModel.saveFollowChannel(
                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                    args.channelId,
                    args.channelLogin,
                    args.channelName,
                    setting,
                    requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                    !requireContext().prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
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

    private fun onShare() {
        startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/${args.channelLogin}")
            args.channelName?.let {
                putExtra(Intent.EXTRA_TITLE, it)
            }
            type = "text/plain"
        }, null))
    }

    private fun onDownload() {
        viewModel.stream.value?.let {
            DownloadDialog.newStreamInstance(
                id = it.id,
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                channelImage = it.channelImage,
                gameId = it.gameId,
                gameSlug = it.gameSlug,
                gameName = it.gameName,
                title = it.title,
                thumbnail = it.thumbnail,
                createdAt = it.createdAt,
            ).show(childFragmentManager, null)
        }
    }

    private fun onSortClick() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "1" -> VideosSortDialog.newInstance(
                    sort = videosViewModel.sort,
                    period = videosViewModel.period,
                    type = videosViewModel.type,
                    saved = args.channelId?.let { videosViewModel.getChannelSort(it) } != null,
                    tab = "videos",
                    context = "channel",
                    hasId = !args.channelId.isNullOrBlank(),
                ).show(childFragmentManager, null)
                "2" -> VideosSortDialog.newInstance(
                    sort = VideosSortDialog.SORT_VIEWS,
                    period = clipsViewModel.period,
                    saved = args.channelId?.let { clipsViewModel.getChannelSort(it) } != null,
                    tab = "clips",
                    context = "channel",
                    hasId = !args.channelId.isNullOrBlank(),
                ).show(childFragmentManager, null)
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

    private fun openTag(tag: String) {
        findNavController().navigate(
            TopStreamsFragmentDirections.actionGlobalTopFragment(
                tags = arrayOf(tag)
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

    override fun onChange(sort: String, sortText: CharSequence, period: String, periodText: CharSequence, type: String, typeText: CharSequence, languages: Array<String>, changed: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "1" -> {
                    if (changed) {
                        videosViewModel.setFilter(sort, type)
                        videosViewModel.sortText.value = getString(R.string.sort_and_type, sortText, typeText)
                    }
                    if (saveSort) {
                        args.channelId?.let { id ->
                            val item = videosViewModel.getChannelSort(id)?.apply {
                                videoSort = sort
                                videoType = type
                            } ?: ChannelSort(
                                id = id,
                                videoSort = sort,
                                videoType = type
                            )
                            videosViewModel.saveChannelSort(item)
                        }
                    }
                    if (saveDefault) {
                        val item = videosViewModel.getChannelSort("default")?.apply {
                            videoSort = sort
                            videoType = type
                        } ?: ChannelSort(
                            id = "default",
                            videoSort = sort,
                            videoType = type
                        )
                        videosViewModel.saveChannelSort(item)
                    }
                }
                "2" -> {
                    if (changed) {
                        clipsViewModel.setFilter(period)
                        clipsViewModel.sortText.value = getString(R.string.sort_and_period, sortText, periodText)
                    }
                    if (saveSort) {
                        args.channelId?.let { id ->
                            val item = clipsViewModel.getChannelSort(id)?.apply {
                                clipPeriod = period
                            } ?: ChannelSort(
                                id = id,
                                clipPeriod = period
                            )
                            clipsViewModel.saveChannelSort(item)
                        }
                    }
                    if (saveDefault) {
                        val item = clipsViewModel.getChannelSort("default")?.apply {
                            clipPeriod = period
                        } ?: ChannelSort(
                            id = "default",
                            clipPeriod = period
                        )
                        clipsViewModel.saveChannelSort(item)
                    }
                }
            }
        }
    }

    override fun deleteSavedSort() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabs.getOrNull(currentTabIndex)) {
                "1" -> args.channelId?.let { videosViewModel.getChannelSort(it) }?.let { videosViewModel.deleteChannelSort(it) }
                "2" -> args.channelId?.let { clipsViewModel.getChannelSort(it) }?.let { clipsViewModel.deleteChannelSort(it) }
            }
        }
    }

    override fun scrollToTop() {
        headerState.offsetPx = 0f
        when (tabs.getOrNull(currentTabIndex)) {
            "0" -> suggestionsScrollTop.value++
            "1" -> videosScrollTop.value++
            "2" -> clipsScrollTop.value++
        }
    }

    override fun onNetworkRestored() {
        viewModel.loadStream(
            TwitchApiHelper.getGQLHeaders(requireContext()),
            TwitchApiHelper.getHelixHeaders(requireContext()),
            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
        suggestionsRefresh.value++
        videosRefresh.value++
        clipsRefresh.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                viewModel.loadStream(
                    TwitchApiHelper.getGQLHeaders(requireContext()),
                    TwitchApiHelper.getHelixHeaders(requireContext()),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
                viewModel.isFollowingChannel(
                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                    args.channelId,
                    args.channelLogin,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    TwitchApiHelper.getHelixHeaders(requireContext()),
                )
                suggestionsRefresh.value++
                videosRefresh.value++
                clipsRefresh.value++
            }
            "follow" -> {
                viewModel.saveFollowChannel(
                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                    args.channelId,
                    args.channelLogin,
                    args.channelName,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                    !requireContext().prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
            "unfollow" -> {
                viewModel.deleteFollowChannel(
                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                    args.channelId,
                    requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
            "enableNotifications" -> {
                args.channelId?.let {
                    viewModel.enableNotifications(
                        requireContext().tokenPrefs().getString(C.USER_ID, null),
                        it,
                        requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                        requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                        TwitchApiHelper.getGQLHeaders(requireContext(), true),
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                }
            }
            "disableNotifications" -> {
                args.channelId?.let {
                    viewModel.disableNotifications(
                        requireContext().tokenPrefs().getString(C.USER_ID, null),
                        it,
                        requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                        TwitchApiHelper.getGQLHeaders(requireContext(), true),
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                }
            }
        }
    }
}
