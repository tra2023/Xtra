package com.github.andreyasadchy.xtra.util.m3u8

/**
 * Pure playlist/storage transforms shared by VOD/stream downloads, library
 * conversion and the settings importer. All file/network IO stays platform-side.
 */
object DownloadPlaylists {

    /** Storage-safe file name of a playlist URI (handles %2F-encoded SAF URIs and plain paths). */
    fun basename(uri: String): String = uri.substringAfterLast("%2F").substringAfterLast("/")

    /** Joins a storage directory URI and a child name (`dir%2Fname`). */
    fun joinChild(directoryUri: String, name: String): String = "$directoryUri%2F$name"

    /** Joins a SAF parent URI and a child name, tolerating the `%3A` tree-root form. */
    fun joinDirectory(parentUri: String, name: String): String =
        parentUri + (if (!parentUri.endsWith("%3A")) "%2F" else "") + name

    /** Pure UTF-8 percent-decoder (mirrors `android.net.Uri.decode`, `+` kept literal). */
    fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val result = StringBuilder(value.length)
        val pending = mutableListOf<Byte>()
        fun flushBytes() {
            if (pending.isNotEmpty()) {
                result.append(pending.toByteArray().decodeToString())
                pending.clear()
            }
        }
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 <= value.lastIndex) {
                val hi = hexValue(value[i + 1])
                val lo = hexValue(value[i + 2])
                if (hi >= 0 && lo >= 0) {
                    pending.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            flushBytes()
            result.append(c)
            i++
        }
        flushBytes()
        return result.toString()
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    fun remapSegments(segments: List<Segment>, mapUri: (String) -> String): List<Segment> =
        segments.map { it.copy(uri = mapUri(it.uri)) }

    fun remapForStorage(playlist: MediaPlaylist, segments: List<Segment>, mapUri: (String) -> String): MediaPlaylist =
        playlist.copy(
            initSegmentUri = playlist.initSegmentUri?.let(mapUri),
            segments = remapSegments(segments, mapUri),
        )

    fun totalDurationMs(segments: List<Segment>): Long {
        var total = 0L
        for (segment in segments) {
            total += (segment.duration * 1000f).toLong()
        }
        return total
    }

    fun basenames(segments: List<Segment>): List<String> =
        segments.map { basename(it.uri) }

    fun decodedBasenames(segments: List<Segment>): List<String> =
        segments.map { percentDecode(basename(it.uri)) }

    fun isSiblingPlaylist(url: String?, directoryUri: String, excludeUrl: String?): Boolean =
        url != null && url.substringBeforeLast("%2F") == directoryUri && url != excludeUrl

    class RangeSelection(
        val segments: List<Segment>,
        val downloadDurationMs: Long,
        val startPositionMs: Long,
    )

    /**
     * Selects `[fromMs, toMs)` from a VOD playlist, swapping in muted variants.
     * Mirrors the historical download loop exactly (including the `break` on overshoot).
     */
    fun selectRange(segments: List<Segment>, fromMs: Long, toMs: Long): RangeSelection {
        val selected = mutableListOf<Segment>()
        var totalDuration = 0L
        var downloadDuration = 0L
        var startPosition = -1L
        for (segment in segments) {
            val startTime = totalDuration
            val duration = (segment.duration * 1000f).toLong()
            val endTime = startTime + duration
            if (endTime <= fromMs) {
                totalDuration = endTime
            } else {
                if (startTime < toMs) {
                    selected.add(segment.copy(uri = segment.uri.replace("-unmuted", "-muted")))
                    totalDuration = endTime
                    downloadDuration += duration
                    if (startPosition == -1L) {
                        startPosition = startTime
                    }
                } else {
                    break
                }
            }
        }
        return RangeSelection(selected, downloadDuration, startPosition)
    }
}
