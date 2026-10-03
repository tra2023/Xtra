package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Bridges Android's View-based nested scrolling with the Compose list inside the bottom-sheet
 * dialogs, so dragging the sheet and scrolling the list cooperate.
 *
 * `rememberNestedScrollInteropConnection()` only exists in the Android Compose artifact
 * (it comes from `androidx.compose.ui:ui`'s View interop), so desktop is a no-op — same
 * expect/actual shape as [com.github.andreyasadchy.xtra.ui.paging.SuppressOverscroll].
 */
@Composable
expect fun rememberNestedScrollModifier(): Modifier
