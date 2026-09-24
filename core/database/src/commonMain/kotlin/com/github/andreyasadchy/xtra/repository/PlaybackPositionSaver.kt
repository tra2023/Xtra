package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.PlaybackType
import com.github.andreyasadchy.xtra.model.VideoPosition

/**
 * Persists the playback position for the current item, throttling writes so only
 * meaningful changes (more than 2 seconds) are stored. Shared by the playback services.
 */
class PlaybackPositionSaver(
    private val playerRepository: PlayerRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
) {
    private var lastSavedPosition: Long? = null

    /** Clears the throttling state, e.g. when a new playback service session starts. */
    fun reset() {
        lastSavedPosition = null
    }

    suspend fun save(type: String?, videoId: String?, offlineVideoId: Int?, position: Long) {
        when (type) {
            PlaybackType.VIDEO -> videoId?.toLongOrNull()?.let {
                playerRepository.saveVideoPosition(VideoPosition(it, position))
            }
            PlaybackType.OFFLINE_VIDEO -> offlineVideoId?.let {
                offlineVideosRepository.updatePosition(it, position)
            }
        }
    }

    /**
     * Updates the last saved position and returns true when it differs enough from the previous
     * write. When [persistPosition] is false the position is only tracked for [savePlaybackState]
     * purposes, mirroring the [com.github.andreyasadchy.xtra.util.C.PLAYER_USE_VIDEO_POSITIONS] setting.
     */
    suspend fun saveIfChanged(type: String?, videoId: String?, offlineVideoId: Int?, position: Long, persistPosition: Boolean): Boolean {
        val saved = lastSavedPosition
        if (saved != null && position - saved in 0..2000) {
            return false
        }
        lastSavedPosition = position
        if (persistPosition) {
            save(type, videoId, offlineVideoId, position)
        }
        return true
    }
}
