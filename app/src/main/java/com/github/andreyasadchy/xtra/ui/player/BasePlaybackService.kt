package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.LifecycleService
import com.github.andreyasadchy.xtra.XtraModule
import com.github.andreyasadchy.xtra.model.PlaybackType
import com.github.andreyasadchy.xtra.repository.PlaybackSession
import com.github.andreyasadchy.xtra.repository.PlaybackSessionImpl
import kotlinx.coroutines.flow.MutableSharedFlow

abstract class BasePlaybackService : LifecycleService(), PlaybackSession by PlaybackSessionImpl() {

    lateinit var xtraModule: XtraModule

    val integrity = MutableSharedFlow<String?>()

    protected suspend fun restorePlaybackState() {
        val savedState = xtraModule.playerRepository.getPlaybackStates().firstOrNull()
        xtraModule.playerRepository.deletePlaybackStates()
        if (savedState != null) {
            restore(savedState, xtraModule.json)
        }
    }

    protected suspend fun savePlaybackState(position: Long?, paused: Boolean) {
        xtraModule.playerRepository.savePlaybackStates(listOf(toPlaybackState(position, paused, xtraModule.json)))
    }

    companion object {
        const val STREAM = PlaybackType.STREAM
        const val VIDEO = PlaybackType.VIDEO
        const val CLIP = PlaybackType.CLIP
        const val OFFLINE_VIDEO = PlaybackType.OFFLINE_VIDEO
    }
}
