package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.VideoQuality
import kotlin.math.floor

object VideoQualityUtils {

    private val comparator = compareByDescending<VideoQuality> { it.bitrate }
        .thenByDescending { it.frameRate }
        .thenByDescending { it.resolution }

    fun sortQualities(qualities: List<VideoQuality>): List<VideoQuality> = qualities.sortedWith(comparator)

    fun sortQualitiesInPlace(qualities: MutableList<VideoQuality>) {
        qualities.sortWith(comparator)
    }

    fun filterSupportedCodecs(qualities: List<VideoQuality>, supportedCodecs: List<String>): List<VideoQuality> =
        qualities.filterNot { quality ->
            val codec = quality.codecs?.substringBefore('.')
            (codec == "av01" && !supportedCodecs.contains("av1")) ||
                    ((codec == "hev1" || codec == "hvc1") && !supportedCodecs.contains("h265"))
        }

    /**
     * Sorts [qualities] and builds the selectable list used by streams and videos.
     * Optionally prepends [VideoQuality.AUTO_QUALITY], renames the "source" variant to
     * [VideoQuality.SOURCE_QUALITY], moves the audio variant to [VideoQuality.AUDIO_ONLY_QUALITY]
     * at the end and optionally appends [VideoQuality.CHAT_ONLY_QUALITY]. When
     * [alwaysAddAudioOnly] is false the audio-only entry is only added if the source list
     * actually contains an audio variant.
     */
    fun buildQualities(
        qualities: List<VideoQuality>,
        addAuto: Boolean = false,
        addChatOnly: Boolean = false,
        alwaysAddAudioOnly: Boolean = true,
    ): MutableList<VideoQuality> = sortQualities(qualities).toMutableList().apply {
        if (addAuto) {
            add(0, VideoQuality(VideoQuality.AUTO_QUALITY))
        }
        find { it.name.equals("source", true) }?.let { source ->
            remove(source)
            add(
                if (addAuto) 1 else 0,
                VideoQuality(VideoQuality.SOURCE_QUALITY, source.resolution, source.frameRate, source.bitrate, source.codecs, source.url)
            )
        }
        val audio = find { it.name?.startsWith("audio", true) == true }
        audio?.let { remove(it) }
        if (audio != null || alwaysAddAudioOnly) {
            add(VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY, audio?.resolution, audio?.frameRate, audio?.bitrate, audio?.codecs, audio?.url))
        }
        if (addChatOnly) {
            add(VideoQuality(VideoQuality.CHAT_ONLY_QUALITY))
        }
    }

    /** Sorts [qualities], drops unsupported codecs and appends [VideoQuality.AUDIO_ONLY_QUALITY]. */
    fun buildClipQualities(qualities: List<VideoQuality>, supportedCodecs: List<String>): MutableList<VideoQuality> =
        sortQualities(filterSupportedCodecs(qualities, supportedCodecs)).toMutableList().apply {
            add(VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY))
        }

    fun findQuality(qualities: List<VideoQuality>?, targetQualityString: String?): VideoQuality? {
        val targetQuality = targetQualityString?.split("p")
        return targetQuality?.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull()?.let { targetResolution ->
            val targetFps = targetQuality.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 30
            val last = qualities?.lastOrNull { it.name != VideoQuality.AUDIO_ONLY_QUALITY && it.name != VideoQuality.CHAT_ONLY_QUALITY }
            qualities?.find { quality ->
                val qualityResolution = quality.resolution
                qualityResolution != null
                        && ((targetResolution == qualityResolution
                        && targetFps >= (quality.frameRate?.let { fps -> floor(fps) } ?: 30f))
                        || targetResolution > qualityResolution
                        || quality == last)
            }
        }
    }

    fun selectDefaultQuality(
        qualities: List<VideoQuality>?,
        cellular: Boolean,
        defaultCellularQuality: String?,
        defaultQuality: String?,
        savedQuality: String?,
    ): VideoQuality? {
        val default = (if (cellular) defaultCellularQuality else defaultQuality)?.substringBefore(" ")
        return (when (default) {
            "saved" -> {
                val saved = savedQuality?.substringBefore(" ")
                when (saved) {
                    VideoQuality.AUTO_QUALITY -> qualities?.find { it.name == VideoQuality.AUTO_QUALITY }
                    VideoQuality.AUDIO_ONLY_QUALITY -> qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
                    VideoQuality.CHAT_ONLY_QUALITY -> qualities?.find { it.name == VideoQuality.CHAT_ONLY_QUALITY }
                    else -> findQuality(qualities, saved)
                }
            }
            VideoQuality.AUTO_QUALITY -> qualities?.find { it.name == VideoQuality.AUTO_QUALITY }
            "Source" -> qualities?.find { it.name != VideoQuality.AUTO_QUALITY }
            VideoQuality.AUDIO_ONLY_QUALITY -> qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
            VideoQuality.CHAT_ONLY_QUALITY -> qualities?.find { it.name == VideoQuality.CHAT_ONLY_QUALITY }
            else -> findQuality(qualities, default)
        }) ?: qualities?.firstOrNull()
    }
}
