package com.github.andreyasadchy.xtra.ui.paging

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
actual fun SuppressOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null, content = content)
}
