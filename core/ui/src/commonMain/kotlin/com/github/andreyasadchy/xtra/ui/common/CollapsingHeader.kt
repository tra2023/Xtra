package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * Collapse state for full-Compose pager headers. Owned by the host fragment
 * (survives recompositions) and shared by the nested-scroll connection and
 * the banner container below. Offset is <= 0, bounded by the measured height.
 */
class CollapsingHeaderState {
    var offsetPx by mutableFloatStateOf(0f)
    var fullPx by mutableIntStateOf(0)
    var atTop by mutableStateOf(true)
}

/**
 * Nested-scroll connection for collapsing headers over Compose paging lists.
 * Up-drags collapse first; down-drags only re-expand at the true top
 * ([CollapsingHeaderState.atTop], reported by the lists themselves), so
 * mid-list gestures belong to the list alone. Flings always belong to the
 * list. Single scroll system.
 */
@Composable
fun rememberCollapseConnection(state: CollapsingHeaderState): NestedScrollConnection =
    remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0f) {
                    val old = state.offsetPx
                    state.offsetPx = (old + dy).coerceAtLeast(-state.fullPx.toFloat())
                    return Offset(0f, state.offsetPx - old)
                }
                if (dy > 0f && state.offsetPx < 0f && state.atTop) {
                    val old = state.offsetPx
                    state.offsetPx = (old + dy).coerceAtMost(0f)
                    return Offset(0f, state.offsetPx - old)
                }
                return Offset.Zero
            }
        }
    }

/**
 * Banner container animated by [CollapsingHeaderState]: height follows the
 * collapse offset, content fades out, overflow is clipped. Content measures
 * unbounded so the full height is known from the first frame even while
 * the container is shrinking.
 */
@Composable
fun CollapsingBanner(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val fraction = if (state.fullPx > 0) (-state.offsetPx / state.fullPx).coerceIn(0f, 1f) else 0f
    val height = with(density) { (state.fullPx + state.offsetPx).coerceAtLeast(0f).toDp() }
    Box(
        modifier
            .height(height)
            .clipToBounds()
            .graphicsLayer { alpha = (1f - fraction).coerceIn(0f, 1f) },
    ) {
        Box(
            Modifier
                .wrapContentHeight(unbounded = true)
                .onSizeChanged { state.fullPx = maxOf(state.fullPx, it.height) },
        ) {
            content()
        }
    }
}
