package com.github.andreyasadchy.xtra.ui.paging

import androidx.compose.runtime.Composable

@Composable
actual fun SuppressOverscroll(content: @Composable () -> Unit) {
    content()
}
