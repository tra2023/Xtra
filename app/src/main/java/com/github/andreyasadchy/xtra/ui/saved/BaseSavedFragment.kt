package com.github.andreyasadchy.xtra.ui.saved

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.TabDropdown
import com.github.andreyasadchy.xtra.ui.common.XtraTopBar
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.saved.SavedPagerViewModel.Companion.SavedPagerViewModelFactory
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarksFragment
import com.github.andreyasadchy.xtra.ui.saved.downloads.DownloadsFragment
import com.github.andreyasadchy.xtra.ui.saved.filters.FiltersFragment
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.launch

/**
 * Shared host for the two Saved screens as a full-Compose scaffold: app bar,
 * tab selector and a pager over the bookmarks, downloads and filters lists.
 * The lists stay in their fragments (hosted through [AndroidFragment], like the
 * channel Chat/About tabs) because the downloads list is bound to its
 * foreground services through the fragment instance looked up by
 * [MainActivity]. Hosts own the import launchers, navigation and dialogs.
 *
 * Subclasses only pick the tab selector: [SavedPagerFragment] renders a tab row,
 * [SavedMediaFragment] (the "use tabs for the Saved page" opt-out) renders a
 * dropdown without swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
abstract class BaseSavedFragment : BaseNetworkFragment(), Scrollable, FragmentHost {

    /** Tab row when true, dropdown chooser (no swipe) when false. */
    protected abstract val useTabs: Boolean

    private val viewModel: SavedPagerViewModel by viewModels { SavedPagerViewModelFactory }
    override var enableNetworkCheck = false

    private var folderResultLauncher: ActivityResultLauncher<Intent>? = null
    private var fileResultLauncher: ActivityResultLauncher<Intent>? = null

    private var tabs: List<String> = listOf("0")
    private var initialTabIndex: Int = 0
    private var currentTabIndex by mutableIntStateOf(0)

    override val currentFragment: Fragment?
        get() = when (tabs.getOrNull(currentTabIndex)) {
            "1" -> childFragmentManager.fragments.filterIsInstance<DownloadsFragment>().firstOrNull()
            "2" -> childFragmentManager.fragments.filterIsInstance<FiltersFragment>().firstOrNull()
            else -> childFragmentManager.fragments.filterIsInstance<BookmarksFragment>().firstOrNull()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabs = computeTabs()
        initialTabIndex = computeInitialTab()
        folderResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    viewModel.saveFolders(it.toString())
                }
            }
        }
        fileResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val list = mutableListOf<String>()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        val item = clipData.getItemAt(i)
                        item.uri?.let {
                            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            list.add(it.toString())
                        }
                    }
                } ?: result.data?.data?.let {
                    requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    list.add(it.toString())
                }
                viewModel.saveVideos(list)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                ProvideXtraLocals(activity) {
                    XtraTheme(themeId = theme) {
                        SavedScreen()
                    }
                }
            }
        }
    }

    private fun computeTabs(): List<String> {
        val tabList = requireContext().prefs().getString(C.UI_SAVED_TABS, null).let { tabPref ->
            val defaultTabs = C.DEFAULT_SAVED_TABS.split(',')
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
        }.ifEmpty { listOf("0") }
    }

    private fun computeInitialTab(): Int {
        val tabList = requireContext().prefs().getString(C.UI_SAVED_TABS, null)?.split(',') ?: C.DEFAULT_SAVED_TABS.split(',')
        val defaultItem = tabList.find { it.split(':')[1] != "0" }?.split(':')?.get(0) ?: "0"
        return tabs.indexOf(defaultItem).takeIf { it != -1 } ?: 0
    }

    @Composable
    private fun SavedScreen() {
        val pagerState = rememberPagerState(initialPage = initialTabIndex) { tabs.size.coerceAtLeast(1) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { currentTabIndex = it }
        }
        val activity = requireActivity() as MainActivity
        val isLoggedIn = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        val liftOptOut = !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)
        Scaffold(
            topBar = {
                XtraTopBar(
                    title = stringResource(R.string.saved),
                    isLoggedIn = isLoggedIn,
                    liftOptOut = liftOptOut,
                    onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                    onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                    onLogin = { onLoginClick(isLoggedIn, activity) },
                    up = { findNavController().navigateUp() },
                    extraOverflow = if (tabs.getOrNull(currentTabIndex) == "1") {
                        listOf(
                            getString(R.string.import_folders) to { folderResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) },
                            getString(R.string.import_files) to {
                                fileResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                })
                            },
                        )
                    } else emptyList(),
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            if (useTabs) {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    val tabTitles = tabs.map { tabId ->
                        stringResource(
                            when (tabId) {
                                "1" -> R.string.downloads
                                "2" -> R.string.filters
                                else -> R.string.bookmarks
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
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = tabs.size,
                    ) { page ->
                        SavedPage(tabs.getOrNull(page))
                    }
                }
            } else {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    val tabTitles = tabs.map { tabId ->
                        stringResource(
                            when (tabId) {
                                "1" -> R.string.downloads
                                "2" -> R.string.filters
                                else -> R.string.bookmarks
                            }
                        )
                    }
                    if (tabTitles.size > 1) {
                        TabDropdown(
                            titles = tabTitles,
                            selectedIndex = currentTabIndex,
                            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = tabs.size,
                        userScrollEnabled = false,
                    ) { page ->
                        SavedPage(tabs.getOrNull(page))
                    }
                }
            }
        }
    }

    @Composable
    private fun SavedPage(tab: String?) {
        when (tab) {
            "1" -> AndroidFragment<DownloadsFragment>(modifier = Modifier.fillMaxSize())
            "2" -> AndroidFragment<FiltersFragment>(modifier = Modifier.fillMaxSize())
            else -> AndroidFragment<BookmarksFragment>(modifier = Modifier.fillMaxSize())
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

    override fun initialize() {
        // No adapter: the tab fragments initialize themselves.
    }

    override fun onNetworkRestored() {
    }

    override fun scrollToTop() {
        (currentFragment as? Scrollable)?.scrollToTop()
    }
}
