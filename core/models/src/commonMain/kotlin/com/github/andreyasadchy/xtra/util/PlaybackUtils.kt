package com.github.andreyasadchy.xtra.util

object PlaybackUtils {

    /**
     * Whether playback should be reduced to audio-only instead of paused when the
     * player view is stopped. Pure predicate shared by the playback services.
     */
    fun shouldKeepBackgroundAudio(
        isInPipMode: Boolean,
        isInteractive: Boolean,
        backgroundAudio: Boolean,
        backgroundAudioLocked: Boolean,
        backgroundAudioPipClosed: Boolean,
        backgroundAudioPipLocked: Boolean,
    ): Boolean =
        (!isInPipMode && isInteractive && backgroundAudio)
                || (!isInPipMode && !isInteractive && backgroundAudioLocked)
                || (isInPipMode && isInteractive && backgroundAudioPipClosed)
                || (isInPipMode && !isInteractive && backgroundAudioPipLocked)
}
