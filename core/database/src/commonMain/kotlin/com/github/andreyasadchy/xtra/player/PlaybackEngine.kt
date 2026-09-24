package com.github.andreyasadchy.xtra.player

import com.github.andreyasadchy.xtra.model.VideoQuality

enum class SourceFormat {
    /** HLS, e.g. a VOD or an unlisted video. */
    HLS,

    /** Live HLS with load-error handling and live configuration. */
    HLS_LIVE,

    /** Progressive media (clips). */
    PROGRESSIVE,

    /** Let the engine decide from the URI (offline downloads). */
    AUTO,
}

data class SourceRequest(
    val url: String,
    val format: SourceFormat,
    val position: Long? = null,
    val playWhenReady: Boolean = true,
    val audioOnly: Boolean = false,
    val speed: Float = 1f,
    val volume: Float = 1f,
)

/**
 * Platform-agnostic playback operations implemented by the Android ExoPlayer service.
 * The [PlayerController] only drives this interface so the orchestration stays in commonMain.
 */
interface PlaybackEngine {
    fun setSource(request: SourceRequest)

    /** Replaces the current media item's URI, keeping its configuration. */
    fun replaceUri(url: String, position: Long?)

    fun prepare()

    fun stop()

    fun pause()

    fun setVolume(volume: Float)

    fun setVideoEnabled(enabled: Boolean)

    /** Clears any video track override and re-enables the video track. */
    fun resetVideoTracks()

    fun selectQuality(quality: VideoQuality)

    fun setSubtitlesEnabled(enabled: Boolean)

    val playWhenReady: Boolean

    val currentPosition: Long

    val hasVideoTracks: Boolean

    val currentUri: String?
}

/** Read/write access to the player preferences needed by [PlayerController]. */
interface PlayerPrefs {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getFloat(key: String, default: Float): Float
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String?)
}
