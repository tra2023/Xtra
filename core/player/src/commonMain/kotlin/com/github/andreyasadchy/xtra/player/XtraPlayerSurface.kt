package com.github.andreyasadchy.xtra.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.compose.MediampPlayerSurface

/**
 * Shared Compose Multiplatform video surface. The concrete view is provided by the
 * backend-specific surface provider (ExoPlayer `PlayerView` on Android, MPV on desktop).
 */
@Composable
fun XtraPlayerSurface(
    player: MediampPlayer,
    modifier: Modifier = Modifier,
) {
    MediampPlayerSurface(player, modifier.fillMaxSize())
}
