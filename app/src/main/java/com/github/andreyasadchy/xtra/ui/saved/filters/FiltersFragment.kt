package com.github.andreyasadchy.xtra.ui.saved.filters

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.navigation.fragment.findNavController
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.filters.FilterListItem
import com.github.andreyasadchy.xtra.ui.filters.FiltersScreen
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.saved.filters.FiltersViewModel.Companion.FiltersViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FiltersFragment : PagedListFragment(), Scrollable {

    private val viewModel: FiltersViewModel by viewModels { FiltersViewModelFactory }
    private var pagingDiffer: AsyncPagingDataDiffer<SavedFilter>? = null
    private var collectionJob: Job? = null
    private var gridState by mutableStateOf<LazyGridState?>(null)
    private var deleteDialog: AlertDialog? = null
    private var bottomInset by mutableIntStateOf(0)
    private var pageVersion by mutableIntStateOf(0)
    private var insertionScroll by mutableIntStateOf(0)
    private var loading by mutableStateOf(true)
    override var enableNetworkCheck = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        var receivedInsertion = false
        bottomInset = 0
        pageVersion = 0
        insertionScroll = 0
        loading = true
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<SavedFilter>() {
                override fun areItemsTheSame(oldItem: SavedFilter, newItem: SavedFilter): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: SavedFilter, newItem: SavedFilter): Boolean =
                    oldItem.gameId == newItem.gameId && oldItem.gameSlug == newItem.gameSlug &&
                            oldItem.gameName == newItem.gameName && oldItem.tags == newItem.tags &&
                            oldItem.languages == newItem.languages
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    if (receivedInsertion && position == 0) {
                        insertionScroll++
                    }
                    receivedInsertion = true
                    pageVersion++
                }

                override fun onRemoved(position: Int, count: Int) {
                    pageVersion++
                }

                override fun onMoved(fromPosition: Int, toPosition: Int) {
                    pageVersion++
                }

                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    pageVersion++
                }
            },
        )
        pagingDiffer = differ
        return ComposeView(requireContext()).apply {
            id = R.id.swipeRefresh
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val configuration = LocalConfiguration.current
                val prefs = requireContext().prefs()
                val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
                    if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                        prefs.getString(C.UI_THEME_DARK_ON, "0") ?: "0"
                    } else {
                        prefs.getString(C.UI_THEME_DARK_OFF, "2") ?: "2"
                    }
                } else {
                    prefs.getString(C.THEME, "0") ?: "0"
                }
                val columns = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    prefs.getString(C.PORTRAIT_COLUMN_COUNT, "1")?.toIntOrNull() ?: 1
                } else {
                    prefs.getString(C.LANDSCAPE_COLUMN_COUNT, "2")?.toIntOrNull() ?: 2
                }
                val state = rememberLazyGridState()
                DisposableEffect(state) {
                    gridState = state
                    onDispose { gridState = null }
                }
                val scrollRequest = insertionScroll
                LaunchedEffect(scrollRequest) {
                    if (scrollRequest > 0) {
                        state.animateScrollToItem(0)
                    }
                }
                val version = pageVersion
                val snapshot = remember(version) { differ.snapshot() }
                val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
                XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                    FiltersScreen(
                        itemCount = snapshot.size,
                        itemKey = { index -> snapshot[index]?.id ?: "placeholder:$index" },
                        itemAt = { index ->
                            if (index < differ.itemCount) differ.getItem(index)
                            snapshot[index]?.let { item ->
                                FilterListItem(
                                    id = item.id,
                                    gameName = item.gameName,
                                    tags = item.tags?.split(',')?.let { resources.getQuantityString(R.plurals.tags, it.size, it.joinToString()) },
                                    languages = item.languages?.split(',')?.let { resources.getQuantityString(R.plurals.languages, it.size, it.joinToString()) },
                                )
                            }
                        },
                        loading = loading,
                        columns = columns,
                        state = state,
                        emptyText = getString(R.string.nothing_here),
                        optionsText = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                        deleteText = getString(R.string.delete),
                        onOpen = { id -> differ.snapshot().items.find { it.id == id }?.let(::openFilter) },
                        onDelete = { id -> differ.snapshot().items.find { it.id == id }?.let(::confirmDelete) },
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        bottomPadding = with(LocalDensity.current) { bottomInset.toDp() },
                        cardMargin = if (!material3) 0.dp else if (prefs.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
                        cornerRadius = if (!material3) 0.dp else when (prefs.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
                            "1" -> 9.dp
                            "2" -> 0.dp
                            else -> 12.dp
                        },
                        compactText = material3 && prefs.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
                        material3 = material3,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { gridState?.canScrollBackward == true }.collectLatest { updateAppBar(it) }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            bottomInset = if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                0
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun initialize() {
        if (collectionJob != null) return
        val differ = pagingDiffer ?: return
        collectionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.flow.collectLatest { differ.submitData(it) }
                }
                launch {
                    differ.loadStateFlow.collectLatest {
                        loading = it.refresh is LoadState.Loading
                        pageVersion++
                    }
                }
                launch {
                    differ.onPagesUpdatedFlow.collectLatest { pageVersion++ }
                }
            }
        }
    }

    private fun updateAppBar(scrolled: Boolean) {
        val parent = parentFragment ?: return
        if ((parent as? FragmentHost)?.currentFragment !== this || !isResumed) return
        if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
            parent.view?.findViewById<AppBarLayout>(R.id.appBar)?.apply {
                setLiftOnScrollTargetView(view)
                isLifted = scrolled
            }
        }
    }

    private fun openFilter(item: SavedFilter) {
        findNavController().navigate(
            if (item.gameId != null || item.gameSlug != null || item.gameName != null) {
                if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                    GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                        gameId = item.gameId,
                        gameSlug = item.gameSlug,
                        gameName = item.gameName,
                        tags = item.tags?.split(',')?.toTypedArray(),
                        languages = item.languages?.split(',')?.toTypedArray(),
                    )
                } else {
                    GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                        gameId = item.gameId,
                        gameSlug = item.gameSlug,
                        gameName = item.gameName,
                        tags = item.tags?.split(',')?.toTypedArray(),
                        languages = item.languages?.split(',')?.toTypedArray(),
                    )
                }
            } else {
                TopStreamsFragmentDirections.actionGlobalTopFragment(
                    tags = item.tags?.split(',')?.toTypedArray(),
                    languages = item.languages?.split(',')?.toTypedArray(),
                )
            }
        )
    }

    private fun confirmDelete(item: SavedFilter) {
        deleteDialog?.dismiss()
        val delete = getString(R.string.delete)
        deleteDialog = requireActivity().getAlertDialogBuilder()
            .setTitle(delete)
            .setMessage(getString(R.string.delete_filter_message))
            .setPositiveButton(delete) { _, _ -> viewModel.delete(item) }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    override fun scrollToTop() {
        val state = gridState ?: return
        viewLifecycleOwner.lifecycleScope.launch { state.scrollToItem(0) }
    }

    override fun onNetworkRestored() {
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
    }

    override fun onDestroyView() {
        collectionJob?.cancel()
        collectionJob = null
        deleteDialog?.dismiss()
        deleteDialog = null
        gridState = null
        pagingDiffer = null
        super.onDestroyView()
    }
}
