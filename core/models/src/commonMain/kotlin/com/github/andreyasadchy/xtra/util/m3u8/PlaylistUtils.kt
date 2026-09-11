package com.github.andreyasadchy.xtra.util.m3u8

object PlaylistUtils {
    private val targetDurationRegex = Regex("#EXT-X-TARGETDURATION:(\\d+)\\b")
    private val idRegex = Regex("ID=\"(.+?)\"")
    private val startDateRegex = Regex("START-DATE=\"(.+?)\"")
    private val classRegex = Regex("CLASS=\"(.+?)\"")
    private val endDateRegex = Regex("END-DATE=\"(.+?)\"")
    private val durationAttrRegex = Regex("DURATION=(.+?)(?:,|$)")
    private val plannedDurationRegex = Regex("PLANNED-DURATION=(.+?)(?:,|$)")
    private val adRegex = Regex("X-TV-TWITCH-AD-.+?=\"(.+?)\"")
    private val mapUriRegex = Regex("URI=\"(.+?)\"")
    private val extInfDurationRegex = Regex("#EXTINF:([\\d.]+)\\b")
    private val extInfTitleRegex = Regex("#EXTINF:[\\d.]+\\b,(.+)")

    fun parseMediaPlaylist(text: String): MediaPlaylist {
        var targetDuration = 10
        val dateRanges = mutableListOf<DateRange>()
        var programDateTime: String? = null
        var initSegmentUri: String? = null
        val segments = mutableListOf<Segment>()
        var segmentInfo: Pair<Float, String?>? = null
        var end = false
        text.lineSequence().forEach { line ->
            if (line.isNotBlank()) {
                if (line.startsWith('#')) {
                    when {
                        line.startsWith("#EXT-X-TARGETDURATION") -> {
                            targetDurationRegex.find(line)?.groupValues?.getOrNull(1)
                                ?.toIntOrNull()?.let { targetDuration = it }
                        }
                        line.startsWith("#EXT-X-DATERANGE") -> {
                            val id = idRegex.find(line)?.groupValues?.getOrNull(1)
                            val startDate = startDateRegex.find(line)?.groupValues?.getOrNull(1)
                            if (id != null && startDate != null) {
                                dateRanges.add(
                                    DateRange(
                                        id = id,
                                        rangeClass = classRegex.find(line)?.groupValues?.getOrNull(1),
                                        startDate = startDate,
                                        endDate = endDateRegex.find(line)?.groupValues?.getOrNull(1),
                                        duration = durationAttrRegex.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull(),
                                        plannedDuration = plannedDurationRegex.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull(),
                                        ad = adRegex.containsMatchIn(line)
                                    )
                                )
                            }
                        }
                        line.startsWith("#EXT-X-PROGRAM-DATE-TIME") -> {
                            programDateTime = line.substringAfter("#EXT-X-PROGRAM-DATE-TIME:")
                        }
                        line.startsWith("#EXT-X-MAP") -> {
                            mapUriRegex.find(line)?.groupValues?.getOrNull(1)?.let { initSegmentUri = it }
                        }
                        line.startsWith("#EXTINF") -> {
                            extInfDurationRegex.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { duration ->
                                val title = extInfTitleRegex.find(line)?.groupValues?.getOrNull(1)
                                segmentInfo = Pair(duration, title)
                            }
                        }
                        line.startsWith("#EXT-X-ENDLIST") -> {
                            end = true
                        }
                    }
                } else {
                    segmentInfo?.let {
                        segments.add(Segment(line, it.first, it.second, programDateTime))
                        segmentInfo = null
                    }
                }
            }
        }
        return MediaPlaylist(targetDuration, dateRanges, initSegmentUri, segments, end)
    }

    fun writeMediaPlaylist(playlist: MediaPlaylist): String {
        return buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:${if (playlist.initSegmentUri != null) 6 else 3}\n")
            append("#EXT-X-PLAYLIST-TYPE:EVENT\n")
            append("#EXT-X-TARGETDURATION:${playlist.targetDuration}\n")
            append("#EXT-X-MEDIA-SEQUENCE:0")
            if (playlist.initSegmentUri != null) {
                append("\n#EXT-X-MAP:URI=\"${playlist.initSegmentUri}\"")
            }
            playlist.segments.forEach {
                append("\n#EXTINF:${it.duration}\n")
                append(it.uri)
            }
            append("\n#EXT-X-ENDLIST")
        }
    }
}
