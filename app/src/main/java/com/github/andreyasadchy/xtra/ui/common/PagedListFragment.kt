package com.github.andreyasadchy.xtra.ui.common

import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.Flow

abstract class PagedListFragment : BaseNetworkFragment(), IntegrityDialog.Listener {

    protected var pagingContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    protected var pagingBottomInset by mutableIntStateOf(0)

    protected fun createPagingView(): View = ComposeView(requireContext()).apply {
        id = R.id.swipeRefresh
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val theme = rememberThemeId()
            ProvideXtraLocals(activity) {
                XtraTheme(themeId = theme) {
                    pagingContent?.invoke()
                }
            }
        }
        // NOTE: never set an inset listener on the ComposeView itself: it would
        // replace Compose's internal listener that feeds WindowInsets into the
        // composition (statusBars padding etc. would silently stop working).
        // Listen on the parent once attached instead, without consuming.
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                (v.parent as? View)?.let { parent ->
                    ViewCompat.setOnApplyWindowInsetsListener(parent) { _, insets ->
                        pagingBottomInset = if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom else 0
                        insets
                    }
                    ViewCompat.requestApplyInsets(parent)
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    @Composable
    protected fun <T : Any> PagingContent(
        flow: Flow<PagingData<T>>,
        refreshSignal: Int,
        retrySignal: Int,
        scrollTopSignal: Int = 0,
        enableScrollTop: Boolean = true,
        enableRefresh: Boolean = true,
        itemKey: ((Int) -> Any)? = null,
        keyForItem: ((T) -> Any?)? = null,
        // Bottom inset override for lists hosted outside createPagingView()
        // (full-Compose screens read WindowInsets directly instead).
        bottomInset: Dp? = null,
        // True-top reports for hosts that coordinate scrolling UI (e.g. a
        // collapsing header that must only re-expand at the list top).
        onAtTopChanged: ((Boolean) -> Unit)? = null,
        // Scroll-to-top action override. Defaults to the Scrollable parent
        // (app bars expand there too), then this list's own grid.
        onScrollTop: (() -> Unit)? = null,
        itemContent: @Composable (T) -> Unit,
    ) {
        PagingGrid(
            flow = flow,
            refreshSignal = refreshSignal,
            retrySignal = retrySignal,
            bottomInset = bottomInset ?: with(LocalDensity.current) { pagingBottomInset.toDp() },
            portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
            scrollTopSignal = scrollTopSignal,
            enableScrollTop = enableScrollTop,
            enableRefresh = enableRefresh,
            itemKey = itemKey,
            keyForItem = keyForItem,
            parentScrollTop = (parentFragment as? Scrollable)?.let { parent -> { parent.scrollToTop() } },
            onScrollTop = onScrollTop,
            onIntegrityFailed = { (activity as? MainActivity)?.getNewIntegrityToken("refresh", childFragmentManager) },
            onAtTopChanged = onAtTopChanged,
            modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
            itemContent = itemContent,
        )
    }

    override fun onDestroyView() {
        pagingContent = null
        pagingBottomInset = 0
        super.onDestroyView()
    }
}
