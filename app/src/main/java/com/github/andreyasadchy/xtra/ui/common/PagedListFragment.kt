package com.github.andreyasadchy.xtra.ui.common

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class PagedListFragment : BaseNetworkFragment(), IntegrityDialog.Listener {

    protected var pagingContent by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
    protected var pagingScrollTop: () -> Unit = {}
    protected var pagingBottomInset by androidx.compose.runtime.mutableIntStateOf(0)

    protected fun createPagingView(): View = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
        id = com.github.andreyasadchy.xtra.R.id.swipeRefresh
        setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val night = androidx.compose.ui.platform.LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val preferences = requireContext().prefs()
            val theme = if (preferences.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) preferences.getString(if (night) C.UI_THEME_DARK_ON else C.UI_THEME_DARK_OFF, if (night) "0" else "2") else preferences.getString(C.THEME, "0")
            com.github.andreyasadchy.xtra.ui.theme.XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                pagingContent?.invoke()
            }
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            pagingBottomInset = if (activity?.findViewById<android.widget.LinearLayout>(com.github.andreyasadchy.xtra.R.id.navBarContainer)?.isVisible == false) insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom else 0
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
    }

    @androidx.compose.runtime.Composable
    protected fun <T : Any> PagingContent(
        flow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<T>>,
        refreshSignal: Int,
        retrySignal: Int,
        enableScrollTop: Boolean = true,
        enableRefresh: Boolean = true,
        itemContent: @androidx.compose.runtime.Composable (T) -> Unit,
    ) {
        val items = flow.collectAsLazyPagingItems()
        val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.runtime.DisposableEffect(state) {
            pagingScrollTop = { scope.launch { state.scrollToItem(0) } }
            onDispose { pagingScrollTop = {} }
        }
        androidx.compose.runtime.LaunchedEffect(refreshSignal) { if (refreshSignal > 0) items.refresh() }
        androidx.compose.runtime.LaunchedEffect(retrySignal) { if (retrySignal > 0) items.retry() }
        val error = listOf(items.loadState.refresh, items.loadState.append, items.loadState.prepend).filterIsInstance<LoadState.Error>().firstOrNull()
        androidx.compose.runtime.LaunchedEffect(error) {
            if (error?.error?.message == C.FAILED_INTEGRITY_CHECK) (activity as? MainActivity)?.getNewIntegrityToken("refresh", childFragmentManager)
        }
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val portrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val columns = requireContext().prefs().getString(if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT, if (portrait) "1" else "2")?.toIntOrNull() ?: 1
        com.github.andreyasadchy.xtra.ui.paging.PagingScaffold(
            itemCount = items.itemCount,
            refreshing = items.loadState.refresh is LoadState.Loading,
            loadingMore = items.loadState.append is LoadState.Loading || items.loadState.prepend is LoadState.Loading,
            errorText = error?.let { getString(com.github.andreyasadchy.xtra.R.string.error, it.error.message.orEmpty()) },
            emptyText = getString(com.github.andreyasadchy.xtra.R.string.nothing_here),
            retryText = getString(com.github.andreyasadchy.xtra.R.string.retry),
            scrollTopText = getString(com.github.andreyasadchy.xtra.R.string.scroll_top),
            state = state, columns = columns,
            onRefresh = { items.refresh() }, onRetry = { items.retry() },
            onScrollTop = { (parentFragment as? Scrollable)?.scrollToTop() ?: pagingScrollTop() },
            enableScrollTop = enableScrollTop && requireContext().prefs().getBoolean(C.UI_SCROLL_TOP, true),
            enableRefresh = enableRefresh,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { pagingBottomInset.toDp() }),
            modifier = androidx.compose.ui.Modifier.nestedScroll(androidx.compose.ui.platform.rememberNestedScrollInteropConnection()),
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
                        } catch (e: Exception) {

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