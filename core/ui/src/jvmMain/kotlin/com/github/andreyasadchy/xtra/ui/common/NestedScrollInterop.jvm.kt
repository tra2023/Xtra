package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop has no View hierarchy, so there is nothing to bridge. */
@Composable
actual fun rememberNestedScrollModifier(): Modifier = Modifier
