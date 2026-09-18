package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.SortBarBinding
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.BookmarkIgnoredUser
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.ui.bookmarks.BookmarksList
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarksViewModel.Companion.BookmarksViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Saved bookmarks as a Compose card list. The list is a plain Room flow, sorted
 * client-side by [BookmarksSortDialog]; [BookmarksMapper] turns each bookmark
 * into the shared `BookmarkListItem` row.
 */
class BookmarksFragment : BaseNetworkFragment(), Scrollable, Sortable, BookmarksSortDialog.OnFilter, IntegrityDialog.Listener {

    private val viewModel: BookmarksViewModel by viewModels { BookmarksViewModelFactory }
    override var enableNetworkCheck = false

    private var gridState by mutableStateOf<LazyGridState?>(null)
    private var bottomInset by mutableIntStateOf(0)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private var positions by mutableStateOf<List<VideoPosition>?>(null)
    private var ignored by mutableStateOf<List<BookmarkIgnoredUser>?>(null)
    private var mapper by mutableStateOf<BookmarksMapper?>(null)
    private var loaded by mutableStateOf(false)
    private var insertionScroll by mutableIntStateOf(0)
    private var firstId: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bottomInset = 0
        bookmarks = emptyList()
        loaded = false
        insertionScroll = 0
        firstId = null
        return ComposeView(requireContext()).apply {
            id = R.id.swipeRefresh
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val configuration = LocalConfiguration.current
                val prefs = requireContext().prefs()
                val theme = rememberThemeId()
                val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val columns = prefs.getString(
                    if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT,
                    if (portrait) "1" else "2",
                )?.toIntOrNull() ?: 1
                val state = rememberLazyGridState()
                DisposableEffect(state) {
                    gridState = state
                    onDispose { gridState = null }
                }
                LaunchedEffect(insertionScroll) { if (insertionScroll > 0) state.animateScrollToItem(0) }
                val list = bookmarks
                val currentPositions = positions
                val currentIgnored = ignored
                val currentMapper = mapper
                val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
                XtraTheme(themeId = theme) {
                    BookmarksList(
                        itemCount = list.size,
                        itemKey = { list[it].id },
                        itemAt = { index -> list.getOrNull(index)?.let { currentMapper?.item(it, currentPositions, currentIgnored) } },
                        columns = columns,
                        state = state,
                        emptyText = getString(R.string.nothing_here),
                        optionsText = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                        deleteText = getString(R.string.delete),
                        showEmpty = loaded,
                        onOpen = { id -> list.find { it.id == id }?.let { currentMapper?.open(it, currentPositions) } },
                        onChannel = { id -> list.find { it.id == id }?.let { currentMapper?.channel(it) } },
                        onGame = { id -> list.find { it.id == id }?.let { currentMapper?.game(it) } },
                        onDelete = { id -> list.find { it.id == id }?.let { currentMapper?.action(it, R.id.delete) } },
                        onAction = { id, action -> list.find { it.id == id }?.let { currentMapper?.action(it, action) } },
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        bottomPadding = with(LocalDensity.current) { bottomInset.toDp() },
                        cardMargin = if (!material3) 0.dp else if (prefs.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
                        cornerRadius = if (!material3) {
                            0.dp
                        } else {
                            when (prefs.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
                                "1" -> 9.dp
                                "2" -> 0.dp
                                else -> 12.dp
                            }
                        },
                        material3 = material3,
                    )
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
        mapper = BookmarksMapper(this, {
            viewModel.updateVideo(
                requireContext().filesDir.path,
                it,
                TwitchApiHelper.getGQLHeaders(requireContext()),
                TwitchApiHelper.getHelixHeaders(requireContext()),
                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }, {
            DownloadDialog.newVideoInstance(
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
                durationSeconds = it.durationSeconds,
                type = it.type,
                animatedPreviewUrl = it.animatedPreviewURL,
            ).show(childFragmentManager, null)
        }, {
            viewModel.vodIgnoreUser(it)
        }, {
            val delete = getString(R.string.delete)
            requireActivity().getAlertDialogBuilder()
                .setTitle(delete)
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(delete) { _, _ -> viewModel.delete(it) }
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show()
        })
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            bottomInset = if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else 0
            windowInsets
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = viewModel.getChannelSort("bookmarks")
                viewModel.setFilter(
                    sort = sortValues?.videoSort,
                    order = sortValues?.videoType,
                )
                viewModel.sortText.value = getString(
                    R.string.sort_and_order,
                    getString(
                        when (viewModel.sort) {
                            BookmarksSortDialog.SORT_EXPIRES_AT -> R.string.deletion_date
                            BookmarksSortDialog.SORT_CREATED_AT -> R.string.creation_date
                            BookmarksSortDialog.SORT_SAVED_AT -> R.string.saved_date
                            else -> R.string.saved_date
                        }
                    ),
                    getString(
                        when (viewModel.order) {
                            BookmarksSortDialog.ORDER_DESC -> R.string.descending
                            BookmarksSortDialog.ORDER_ASC -> R.string.ascending
                            else -> R.string.descending
                        }
                    )
                )
            }
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flow.collectLatest { list ->
                    val sorted = if (viewModel.order == BookmarksSortDialog.ORDER_ASC) {
                        when (viewModel.sort) {
                            BookmarksSortDialog.SORT_EXPIRES_AT -> list.sortedWith(compareBy(nullsLast()) { timeLeftSeconds(it) })
                            BookmarksSortDialog.SORT_CREATED_AT -> list.sortedWith(compareBy(nullsLast()) { createdMillis(it) })
                            else -> list.sortedWith(compareBy(nullsLast()) { it.id })
                        }
                    } else {
                        when (viewModel.sort) {
                            BookmarksSortDialog.SORT_EXPIRES_AT -> list.sortedWith(compareByDescending(nullsFirst()) { timeLeftSeconds(it) })
                            BookmarksSortDialog.SORT_CREATED_AT -> list.sortedWith(compareByDescending(nullsFirst()) { createdMillis(it) })
                            else -> list.sortedWith(compareByDescending(nullsFirst()) { it.id })
                        }
                    }
                    // The old adapter scrolled to the top when an item was inserted
                    // at position 0 (a newly saved bookmark), but not on first load.
                    val newFirst = sorted.firstOrNull()?.id
                    if (loaded && newFirst != null && newFirst != firstId) insertionScroll++
                    firstId = newFirst
                    loaded = true
                    bookmarks = sorted
                }
            }
        }
        if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.positions.collectLatest { positions = it }
                }
            }
        }
        if (requireContext().prefs().getBoolean(C.UI_BOOKMARK_TIME_LEFT, true)) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.ignoredUsers.collectLatest { ignored = it }
                }
            }
            viewModel.updateUsers(
                TwitchApiHelper.getGQLHeaders(requireContext()),
                TwitchApiHelper.getHelixHeaders(requireContext()),
                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
        val helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext())
        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            viewModel.updateVideos(requireContext().filesDir.path, helixHeaders)
        }
    }

    /** Remaining archive lifetime in seconds, or null when it does not expire. */
    private fun timeLeftSeconds(bookmark: Bookmark): Long? {
        if (bookmark.type?.lowercase() != "archive") return null
        val createdAt = bookmark.createdAt ?: return null
        val created = Instant.parseOrNull(createdAt)?.takeIf { it.toEpochMilliseconds() > 0 } ?: return null
        val userType = bookmark.userType ?: bookmark.userBroadcasterType
        val days = if (userType.isNullOrBlank()) {
            7
        } else {
            when (userType.lowercase()) {
                "affiliate" -> 14
                else -> 60 // Partners, Prime, Turbo
            }
        }
        val remaining = (created + days.days) - Clock.System.now()
        return remaining.inWholeSeconds.takeIf { remaining.isPositive() }
    }

    private fun createdMillis(bookmark: Bookmark): Long? =
        bookmark.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }

    override fun setupSortBar(sortBar: SortBarBinding) {
        sortBar.root.visibility = View.VISIBLE
        sortBar.root.setOnClickListener {
            BookmarksSortDialog.newInstance(
                sort = viewModel.sort,
                order = viewModel.order,
            ).show(childFragmentManager, null)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sortText.collectLatest {
                    sortBar.sortText.text = it
                }
            }
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, order: String, orderText: CharSequence, changed: Boolean, saveDefault: Boolean) {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (changed) {
                    bookmarks = emptyList()
                    viewModel.setFilter(sort, order)
                    viewModel.sortText.value = getString(R.string.sort_and_order, sortText, orderText)
                }
                if (saveDefault) {
                    val item = viewModel.getChannelSort("bookmarks")?.apply {
                        videoSort = sort
                        videoType = order
                    } ?: ChannelSort(
                        id = "bookmarks",
                        videoSort = sort,
                        videoType = order
                    )
                    viewModel.saveChannelSort(item)
                }
            }
        }
    }

    override fun scrollToTop() {
        viewLifecycleOwner.lifecycleScope.launch { gridState?.scrollToItem(0) }
    }

    override fun onNetworkRestored() {
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "users" -> {
                viewModel.updateUsers(
                    TwitchApiHelper.getGQLHeaders(requireContext()),
                    TwitchApiHelper.getHelixHeaders(requireContext()),
                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
        }
    }

    override fun onDestroyView() {
        mapper = null
        super.onDestroyView()
    }
}
