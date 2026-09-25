package com.github.andreyasadchy.xtra.repository.saved

import com.github.andreyasadchy.xtra.model.ui.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Platform-agnostic download progress holder, ported from
 * `app/.../ui/saved/downloads/DownloadsProgressViewModel.kt`.
 *
 * A plain class (same pattern as the browse controllers): the Android host
 * keeps a thin `ViewModel` delegate for `viewModels()` scoping, while all
 * state logic lives here.
 */
data class DownloadProgressState(
    val live: Boolean,
    val progress: Int,
    val maxProgress: Int,
    val chatProgress: Int,
    val maxChatProgress: Int,
)

class DownloadsProgressTracker {
    private val _progress = MutableStateFlow<Map<Int, DownloadProgressState>>(emptyMap())
    val progress: StateFlow<Map<Int, DownloadProgressState>> = _progress

    fun update(progress: DownloadProgress, live: Boolean) {
        val snapshot = snapshot(progress, live)
        _progress.update { it + (progress.id to snapshot) }
    }

    fun replace(live: Boolean, downloads: List<DownloadProgress>) {
        val snapshots = downloads.associate { it.id to snapshot(it, live) }
        _progress.update { current -> current.filterValues { it.live != live } + snapshots }
    }

    private fun snapshot(progress: DownloadProgress, live: Boolean) = DownloadProgressState(
        live, progress.progress, progress.maxProgress, progress.chatProgress, progress.maxChatProgress,
    )
}
