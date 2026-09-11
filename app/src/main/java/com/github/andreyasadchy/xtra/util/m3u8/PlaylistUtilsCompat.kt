package com.github.andreyasadchy.xtra.util.m3u8

import java.io.InputStream
import java.io.OutputStream

/**
 * Android/JVM IO adapters over the KMP String-based [PlaylistUtils].
 * Keeps existing call sites (`InputStream`/`OutputStream`) working.
 */
fun PlaylistUtils.parseMediaPlaylist(input: InputStream): MediaPlaylist =
    parseMediaPlaylist(input.bufferedReader().readText())

fun PlaylistUtils.writeMediaPlaylist(playlist: MediaPlaylist, output: OutputStream) {
    output.bufferedWriter().use { it.write(writeMediaPlaylist(playlist)) }
}
