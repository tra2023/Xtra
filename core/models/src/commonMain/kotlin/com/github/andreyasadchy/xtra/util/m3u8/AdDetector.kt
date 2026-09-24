package com.github.andreyasadchy.xtra.util.m3u8

/**
 * Platform-agnostic Twitch ad detection shared by playlist polling (parsed m3u8)
 * and the live media3 HLS manifest. Times are milliseconds since the Unix epoch.
 */
object AdDetector {

    data class AdSegment(
        val title: String?,
        val startTimeMs: Long?,
    )

    data class AdRange(
        val id: String,
        val rangeClass: String?,
        val ad: Boolean,
        val startTimeMs: Long?,
        val endTimeMs: Long?,
    )

    fun isAd(segment: AdSegment, ranges: List<AdRange>): Boolean {
        if (segment.title == "Amazon" || segment.title == "Adform" || segment.title == "DCM") {
            return true
        }
        val segmentStartTime = segment.startTimeMs ?: return false
        return ranges.any { range ->
            (range.id.startsWith("stitched-ad-") || range.rangeClass == "twitch-stitched-ad" || range.ad) &&
                    range.startTimeMs?.let { startTime ->
                        range.endTimeMs?.let { endTime -> segmentStartTime in startTime..<endTime } == true
                    } == true
        }
    }
}
