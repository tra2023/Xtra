package com.github.andreyasadchy.xtra.ui.paging

import androidx.compose.runtime.Composable

/**
 * Suppresses the stretch/overscroll effect where the platform supports it
 * (Android). Desktop is a no-op. Kept as expect/actual because
 * `LocalOverscrollConfiguration` is Android-only.
 */
@Composable
expect fun SuppressOverscroll(content: @Composable () -> Unit)
