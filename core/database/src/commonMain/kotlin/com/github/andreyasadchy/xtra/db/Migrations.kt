package com.github.andreyasadchy.xtra.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS translate_all_messages")
    }
}

val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS playback_states1 (type TEXT, streamId TEXT, videoId TEXT, clipId TEXT, offlineVideoId INTEGER, channelId TEXT, channelLogin TEXT, channelName TEXT, channelImage TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, title TEXT, thumbnail TEXT, createdAt TEXT, viewerCount INTEGER, durationSeconds INTEGER, videoType TEXT, videoOffsetSeconds INTEGER, videoCreatedAt TEXT, videoAnimatedPreviewURL TEXT, position INTEGER, paused INTEGER NOT NULL, qualities TEXT, quality TEXT, previousQuality TEXT, restoreQuality INTEGER NOT NULL, playlistUrl TEXT, restorePlaylist INTEGER NOT NULL, skipAccessToken INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO playback_states1 (type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoCreatedAt, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, skipAccessToken, id) SELECT type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoCreatedAt, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, skipAccessToken, id FROM playback_states")
        connection.execSQL("DROP TABLE playback_states")
        connection.execSQL("ALTER TABLE playback_states1 RENAME TO playback_states")
        connection.execSQL("DROP TABLE IF EXISTS custom_proxies")
        connection.execSQL("DROP TABLE IF EXISTS stream_proxies")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_40_41,
    MIGRATION_41_42
)
