package com.github.andreyasadchy.xtra.ui.common

import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.paging.PagingScaffold
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class PagedListFragment : BaseNetworkFragment(), IntegrityDialog.Listener {

    protected var pagingContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    protected var pagingScrollTop: () -> Unit = {}
    protected var pagingBottomInset by mutableIntStateOf(0)

    protected fun createPagingView(): View = ComposeView(requireContext()).apply {
        id = R.id.swipeRefresh
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val theme = rememberThemeId()
            XtraTheme(themeId = theme) {
                pagingContent?.invoke()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            pagingBottomInset = if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom else 0
            WindowInsetsCompat.CONSUMED
        }
    }

    @Composable
    protected fun <T : Any> PagingContent(
        flow: Flow<PagingData<T>>,
        refreshSignal: Int,
        retrySignal: Int,
        enableScrollTop: Boolean = true,
        enableRefresh: Boolean = true,
        itemContent: @Composable (T) -> Unit,
    ) {
        val items = flow.collectAsLazyPagingItems()
        val state = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        DisposableEffect(state) {
            pagingScrollTop = {
                scope.launch {
                    try {
                        if (state.layoutInfo.totalItemsCount > 0) {
                            state.scrollToItem(0)
                        }
                    } catch (_: IndexOutOfBoundsException) {
                        // Concurrent refresh cleared the list.
                    } catch (_: IllegalArgumentException) {
                        // Concurrent refresh made the index invalid.
                    }
                }
            }
            onDispose { pagingScrollTop = {} }
        }
        LaunchedEffect(refreshSignal) { if (refreshSignal > 0) items.refresh() }
        LaunchedEffect(retrySignal) { if (retrySignal > 0) items.retry() }
        val error = listOf(items.loadState.refresh, items.loadState.append, items.loadState.prepend).filterIsInstance<LoadState.Error>().firstOrNull()
        LaunchedEffect(error) {
            if (error?.error?.message == C.FAILED_INTEGRITY_CHECK) (activity as? MainActivity)?.getNewIntegrityToken("refresh", childFragmentManager)
        }
        val configuration = LocalConfiguration.current
        val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val columns = requireContext().prefs().getString(if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT, if (portrait) "1" else "2")?.toIntOrNull() ?: 1
        PagingScaffold(
            itemCount = items.itemCount,
            refreshing = items.loadState.refresh is LoadState.Loading,
            loadingMore = items.loadState.append is LoadState.Loading || items.loadState.prepend is LoadState.Loading,
            errorText = error?.let { getString(R.string.error, it.error.message.orEmpty()) },
            emptyText = getString(R.string.nothing_here),
            retryText = getString(R.string.retry),
            scrollTopText = getString(R.string.scroll_top),
            state = state, columns = columns,
            onRefresh = { items.refresh() }, onRetry = { items.retry() },
            onScrollTop = { (parentFragment as? Scrollable)?.scrollToTop() ?: pagingScrollTop() },
            enableScrollTop = enableScrollTop && requireContext().prefs().getBoolean(C.UI_SCROLL_TOP, true),
            enableRefresh = enableRefresh,
            contentPadding = PaddingValues(bottom = with(LocalDensity.current) { pagingBottomInset.toDp() }),
            modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        ) { index -> items[index]?.let { itemContent(it) } }
    }

    override fun onDestroyView() {
        pagingContent = null
        pagingScrollTop = {}
        pagingBottomInset = 0
        super.onDestroyView()
    }

    fun <T : Any, VH : RecyclerView.ViewHolder> setAdapter(recyclerView: RecyclerView, adapter: PagingDataAdapter<T, VH>) {
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                adapter.unregisterAdapterDataObserver(this)
                adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                        try {
                            if (positionStart == 0) {
                                recyclerView.scrollToPosition(0)
                            }
                        } catch (_: Exception) {
                        }
                    }
                })
            }
        })
        recyclerView.adapter = adapter
    }

    fun shouldShowButton(recyclerView: RecyclerView): Boolean {
        val offset = recyclerView.computeVerticalScrollOffset()
        if (offset < 0) {
            return false
        }
        val extent = recyclerView.computeVerticalScrollExtent()
        val range = recyclerView.computeVerticalScrollRange()
        val percentage = (100f * offset / (range - extent).toFloat())
        return percentage > 3f
    }

    fun <T : Any, VH : RecyclerView.ViewHolder> initializeAdapter(binding: CommonRecyclerViewLayoutBinding, pagingAdapter: PagingDataAdapter<T, VH>, enableSwipeRefresh: Boolean = true, enableScrollTopButton: Boolean = true) {
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    pagingAdapter.loadStateFlow.collectLatest { loadState ->
                        progressBar.isVisible = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount == 0
                        if (enableSwipeRefresh) {
                            swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount != 0
                        }
                        nothingHere.isVisible = loadState.refresh !is LoadState.Loading && pagingAdapter.itemCount == 0
                        if ((loadState.refresh as? LoadState.Error ?:
                            loadState.append as? LoadState.Error ?:
                            loadState.prepend as? LoadState.Error)?.error?.message == C.FAILED_INTEGRITY_CHECK
                        ) {
                            (requireActivity() as? MainActivity)?.getNewIntegrityToken("refresh", childFragmentManager)
                        }
                    }
                }
            }
            if (enableSwipeRefresh) {
                swipeRefresh.isEnabled = true
                swipeRefresh.setOnRefreshListener { pagingAdapter.refresh() }
            }
            if (enableScrollTopButton && requireContext().prefs().getBoolean(C.UI_SCROLL_TOP, true)) {
                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        scrollTop.isVisible = shouldShowButton(recyclerView)
                    }
                })
                scrollTop.setOnClickListener {
                    (parentFragment as? Scrollable)?.scrollToTop()
                    it.visibility = View.GONE
                }
            }
        }
    }
}
