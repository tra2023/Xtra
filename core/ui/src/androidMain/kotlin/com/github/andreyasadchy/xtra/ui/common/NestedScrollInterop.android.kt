package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection

@Composable
actual fun rememberNestedScrollModifier(): Modifier =
    Modifier.nestedScroll(rememberNestedScrollInteropConnection())
