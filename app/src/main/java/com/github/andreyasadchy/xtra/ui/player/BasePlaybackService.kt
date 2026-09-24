package com.github.andreyasadchy.xtra.ui.player

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.LifecycleService
import com.github.andreyasadchy.xtra.XtraModule
import com.github.andreyasadchy.xtra.model.PlaybackType
import com.github.andreyasadchy.xtra.repository.PlaybackSession
import com.github.andreyasadchy.xtra.repository.PlaybackSessionImpl
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.VideoQualityUtils
import com.github.andreyasadchy.xtra.util.prefs
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

    protected fun setDefaultQuality() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val cellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        quality = VideoQualityUtils.selectDefaultQuality(
            qualities = qualities,
            cellular = cellular,
            defaultCellularQuality = prefs().getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved"),
            defaultQuality = prefs().getString(C.PLAYER_DEFAULT_QUALITY, "saved"),
            savedQuality = prefs().getString(C.PLAYER_QUALITY, "720p60"),
        )
    }

    companion object {
        const val STREAM = PlaybackType.STREAM
        const val VIDEO = PlaybackType.VIDEO
        const val CLIP = PlaybackType.CLIP
        const val OFFLINE_VIDEO = PlaybackType.OFFLINE_VIDEO
    }
}
