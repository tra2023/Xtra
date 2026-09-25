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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.ProvideXtraLocals
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.gridColumns
import com.github.andreyasadchy.xtra.ui.common.rememberXtraCardStyle
import com.github.andreyasadchy.xtra.ui.filters.FilterListItem
import com.github.andreyasadchy.xtra.ui.filters.FiltersScreen
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.paging.findById
import com.github.andreyasadchy.xtra.ui.paging.rememberInsertionScroll
import com.github.andreyasadchy.xtra.ui.paging.rememberPagingSnapshot
import com.github.andreyasadchy.xtra.ui.saved.filters.FiltersViewModel.Companion.FiltersViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FiltersFragment : PagedListFragment(), Scrollable {

    private val viewModel: FiltersViewModel by viewModels { FiltersViewModelFactory }
    private var gridState by mutableStateOf<LazyGridState?>(null)
    private var deleteDialog: AlertDialog? = null
    private var bottomInset by mutableIntStateOf(0)
    override var enableNetworkCheck = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bottomInset = 0
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val configuration = LocalConfiguration.current
                val theme = rememberThemeId()
                val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val state = rememberLazyGridState()
                DisposableEffect(state) {
                    gridState = state
                    onDispose { gridState = null }
                }
                val snapshot = rememberPagingSnapshot(viewModel.flow)
                val insertionScroll = snapshot.rememberInsertionScroll { it.id }
                LaunchedEffect(insertionScroll) {
                    if (insertionScroll > 0) {
                        state.animateScrollToItem(0)
                    }
                }
                fun find(id: Int) = snapshot.findById(id) { it.id }
                XtraTheme(themeId = theme) {
                    ProvideXtraLocals(activity) {
                        val columns = gridColumns(portrait)
                        val style = rememberXtraCardStyle()
                        FiltersScreen(
                        itemCount = snapshot.itemCount,
                        itemKey = { index -> snapshot.peek(index)?.id ?: "placeholder:$index" },
                        itemAt = { index ->
                            snapshot[index]?.let { item ->
                                FilterListItem(
                                    id = item.id,
                                    gameName = item.gameName,
                                    tags = item.tags?.split(',')?.let { resources.getQuantityString(R.plurals.tags, it.size, it.joinToString()) },
                                    languages = item.languages?.split(',')?.let { resources.getQuantityString(R.plurals.languages, it.size, it.joinToString()) },
                                )
                            }
                        },
                        loading = snapshot.loading,
                        columns = columns,
                        state = state,
                        emptyText = getString(R.string.nothing_here),
                        optionsText = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                        deleteText = getString(R.string.delete),
                        onOpen = { id -> find(id)?.let(::openFilter) },
                        onDelete = { id -> find(id)?.let(::confirmDelete) },
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        bottomPadding = with(LocalDensity.current) { bottomInset.toDp() },
                        cardMargin = style.cardMargin,
                        cornerRadius = style.cornerRadius,
                        compactText = style.compactText,
                        material3 = style.material3,
                    )
                    }
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
        // No adapter: FiltersScreen collects viewModel.flow through rememberPagingSnapshot.
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
        deleteDialog?.dismiss()
        deleteDialog = null
        gridState = null
        super.onDestroyView()
    }
}
