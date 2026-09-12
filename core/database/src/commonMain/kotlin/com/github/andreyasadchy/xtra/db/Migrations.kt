package com.github.andreyasadchy.xtra.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DELETE FROM emotes")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE videos ADD COLUMN videoId TEXT DEFAULT null")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS local_follows_games (game_id TEXT NOT NULL, game_name TEXT, boxArt TEXT, PRIMARY KEY (game_id))")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT NOT NULL, source_url TEXT NOT NULL, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER NOT NULL, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, status INTEGER NOT NULL, type TEXT, videoId TEXT, id INTEGER NOT NULL, is_vod INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT NOT NULL, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, status INTEGER, type TEXT, videoId TEXT, is_bookmark INTEGER, userType TEXT, id INTEGER NOT NULL, is_vod INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id = id, is_vod = is_vod FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
        connection.execSQL("CREATE TABLE IF NOT EXISTS vod_bookmark_ignored_users (user_id TEXT NOT NULL, PRIMARY KEY (user_id))")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT NOT NULL, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, status INTEGER NOT NULL, type TEXT, videoId TEXT, id INTEGER NOT NULL, is_vod INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT OR IGNORE INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id = id, is_vod = is_vod FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
        connection.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id TEXT NOT NULL, userId TEXT, userLogin TEXT, userName TEXT, userLogo TEXT, gameId TEXT, gameName TEXT, title TEXT, createdAt TEXT, thumbnail TEXT, type TEXT, duration TEXT, PRIMARY KEY (id))")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN userType TEXT DEFAULT null")
        connection.execSQL("ALTER TABLE bookmarks ADD COLUMN userBroadcasterType TEXT DEFAULT null")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS sort_channel (id TEXT NOT NULL, saveSort INTEGER, videoSort TEXT, videoType TEXT, clipPeriod TEXT, PRIMARY KEY (id))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS sort_game (id TEXT NOT NULL, saveSort INTEGER, videoSort TEXT, videoPeriod TEXT, videoType TEXT, videoLanguageIndex INTEGER, clipPeriod TEXT, clipLanguageIndex INTEGER, PRIMARY KEY (id))")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS recent_emotes1 (name TEXT NOT NULL, url1x TEXT, url2x TEXT, url3x TEXT, url4x TEXT, used_at INTEGER NOT NULL, PRIMARY KEY (name))")
        connection.execSQL("INSERT INTO recent_emotes1 (name, url1x, used_at) SELECT name, url, used_at FROM recent_emotes")
        connection.execSQL("DROP TABLE recent_emotes")
        connection.execSQL("ALTER TABLE recent_emotes1 RENAME TO recent_emotes")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS bookmarks1 (videoId TEXT, userId TEXT, userLogin TEXT, userName TEXT, userType TEXT, userBroadcasterType TEXT, userLogo TEXT, gameId TEXT, gameName TEXT, title TEXT, createdAt TEXT, thumbnail TEXT, type TEXT, duration TEXT, animatedPreviewURL TEXT, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO bookmarks1 (videoId, userId, userLogin, userName, userType, userBroadcasterType, userLogo, gameId, gameName, title, createdAt, thumbnail, type, duration) SELECT id, userId, userLogin, userName, userType, userBroadcasterType, userLogo, gameId, gameName, title, createdAt, thumbnail, type, duration FROM bookmarks")
        connection.execSQL("DROP TABLE bookmarks")
        connection.execSQL("ALTER TABLE bookmarks1 RENAME TO bookmarks")
        connection.execSQL("CREATE TABLE IF NOT EXISTS local_follows1 (userId TEXT, userLogin TEXT, userName TEXT, channelLogo TEXT, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO local_follows1 (userId, userLogin, userName, channelLogo) SELECT user_id, user_login, user_name, channelLogo FROM local_follows")
        connection.execSQL("DROP TABLE local_follows")
        connection.execSQL("ALTER TABLE local_follows1 RENAME TO local_follows")
        connection.execSQL("CREATE TABLE IF NOT EXISTS local_follows_games1 (gameId TEXT, gameName TEXT, boxArt TEXT, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO local_follows_games1 (gameId, gameName, boxArt) SELECT game_id, game_name, boxArt FROM local_follows_games")
        connection.execSQL("DROP TABLE local_follows_games")
        connection.execSQL("ALTER TABLE local_follows_games1 RENAME TO local_follows_games")
        connection.execSQL("CREATE TABLE IF NOT EXISTS requests1 (offline_video_id INTEGER NOT NULL, url TEXT NOT NULL, path TEXT NOT NULL, video_id TEXT, video_type TEXT, segment_from INTEGER, segment_to INTEGER, PRIMARY KEY (offline_video_id), FOREIGN KEY('offline_video_id') REFERENCES videos('id') ON DELETE CASCADE)")
        connection.execSQL("INSERT INTO requests1 (offline_video_id, url, path, video_id, segment_from, segment_to) SELECT offline_video_id, url, path, video_id, segment_from, segment_to FROM requests")
        connection.execSQL("DROP TABLE requests")
        connection.execSQL("ALTER TABLE requests1 RENAME TO requests")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS recent_emotes1 (name TEXT NOT NULL, used_at INTEGER NOT NULL, PRIMARY KEY (name))")
        connection.execSQL("INSERT INTO recent_emotes1 (name, used_at) SELECT name, used_at FROM recent_emotes")
        connection.execSQL("DROP TABLE recent_emotes")
        connection.execSQL("ALTER TABLE recent_emotes1 RENAME TO recent_emotes")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS requests1 (offline_video_id INTEGER NOT NULL, url TEXT NOT NULL, path TEXT NOT NULL, PRIMARY KEY (offline_video_id), FOREIGN KEY('offline_video_id') REFERENCES videos('id') ON DELETE CASCADE)")
        connection.execSQL("INSERT INTO requests1 (offline_video_id, url, path) SELECT offline_video_id, url, path FROM requests")
        connection.execSQL("DROP TABLE requests")
        connection.execSQL("ALTER TABLE requests1 RENAME TO requests")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT NOT NULL, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, status INTEGER NOT NULL, type TEXT, videoId TEXT, id INTEGER NOT NULL, is_vod INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
        connection.execSQL("CREATE TABLE IF NOT EXISTS bookmarks1 (videoId TEXT, userId TEXT, userLogin TEXT, userName TEXT, userType TEXT, userBroadcasterType TEXT, userLogo TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, title TEXT, createdAt TEXT, thumbnail TEXT, type TEXT, duration TEXT, animatedPreviewURL TEXT, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO bookmarks1 (videoId, userId, userLogin, userName, userType, userBroadcasterType, userLogo, gameId, gameName, title, createdAt, thumbnail, type, duration, animatedPreviewURL, id) SELECT videoId, userId, userLogin, userName, userType, userBroadcasterType, userLogo, gameId, gameName, title, createdAt, thumbnail, type, duration, animatedPreviewURL, id FROM bookmarks")
        connection.execSQL("DROP TABLE bookmarks")
        connection.execSQL("ALTER TABLE bookmarks1 RENAME TO bookmarks")
        connection.execSQL("CREATE TABLE IF NOT EXISTS local_follows_games1 (gameId TEXT, gameSlug TEXT, gameName TEXT, boxArt TEXT, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO local_follows_games1 (gameId, gameName, boxArt, id) SELECT gameId, gameName, boxArt, id FROM local_follows_games")
        connection.execSQL("DROP TABLE local_follows_games")
        connection.execSQL("ALTER TABLE local_follows_games1 RENAME TO local_follows_games")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT NOT NULL, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, downloadPath TEXT, fromTime INTEGER, toTime INTEGER, status INTEGER NOT NULL, type TEXT, videoId TEXT, quality TEXT, id INTEGER NOT NULL, is_vod INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, status, type, videoId, id, is_vod FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT NOT NULL, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, downloadPath TEXT, fromTime INTEGER, toTime INTEGER, status INTEGER NOT NULL, type TEXT, videoId TEXT, quality TEXT, downloadChat INTEGER, downloadChatEmotes INTEGER, chatProgress INTEGER, chatUrl TEXT, id INTEGER NOT NULL, is_vod INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, downloadPath, fromTime, toTime, status, type, videoId, quality, id, is_vod) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, downloadPath, fromTime, toTime, status, type, videoId, quality, id, is_vod FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE requests")
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, bytes INTEGER NOT NULL, downloadPath TEXT, fromTime INTEGER, toTime INTEGER, status INTEGER NOT NULL, type TEXT, videoId TEXT, clipId TEXT, quality TEXT, downloadChat INTEGER NOT NULL, downloadChatEmotes INTEGER NOT NULL, chatProgress INTEGER NOT NULL, maxChatProgress INTEGER NOT NULL, chatBytes INTEGER NOT NULL, chatOffsetSeconds INTEGER NOT NULL, chatUrl TEXT, playlistToFile INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, bytes, downloadPath, fromTime, toTime, status, type, videoId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, id) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, 0, downloadPath, fromTime, toTime, status, type, videoId, quality, 0, 0, 0, 100, 0, 0, chatUrl, 0, id FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, bytes INTEGER NOT NULL, downloadPath TEXT, fromTime INTEGER, toTime INTEGER, status INTEGER NOT NULL, type TEXT, videoId TEXT, clipId TEXT, quality TEXT, downloadChat INTEGER NOT NULL, downloadChatEmotes INTEGER NOT NULL, chatProgress INTEGER NOT NULL, maxChatProgress INTEGER NOT NULL, chatBytes INTEGER NOT NULL, chatOffsetSeconds INTEGER NOT NULL, chatUrl TEXT, playlistToFile INTEGER NOT NULL, live INTEGER NOT NULL, lastSegmentUrl TEXT, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, bytes, downloadPath, fromTime, toTime, status, type, videoId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, live, id) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, max_progress, downloadPath, fromTime, toTime, status, type, videoId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, 0, id FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS shown_notifications (channelId TEXT NOT NULL, startedAt INTEGER NOT NULL, PRIMARY KEY (channelId))")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS notifications (channelId TEXT NOT NULL, PRIMARY KEY (channelId))")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS translate_all_messages (channelId TEXT NOT NULL, PRIMARY KEY (channelId))")
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS sort_game1 (id TEXT NOT NULL, saveSort INTEGER, videoSort TEXT, videoPeriod TEXT, videoType TEXT, videoLanguages TEXT, clipPeriod TEXT, clipLanguages TEXT, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO sort_game1 (id, saveSort, videoSort, videoPeriod, videoType, clipPeriod) SELECT id, saveSort, videoSort, videoPeriod, videoType, clipPeriod FROM sort_game")
        connection.execSQL("DROP TABLE sort_game")
        connection.execSQL("ALTER TABLE sort_game1 RENAME TO sort_game")
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS sort_game1 (id TEXT NOT NULL, streamSort TEXT, streamTags TEXT, streamLanguages TEXT, videoSort TEXT, videoPeriod TEXT, videoType TEXT, videoLanguages TEXT, clipPeriod TEXT, clipLanguages TEXT, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO sort_game1 (id, videoSort, videoPeriod, videoType, videoLanguages, clipPeriod, clipLanguages) SELECT id, videoSort, videoPeriod, videoType, videoLanguages, clipPeriod, clipLanguages FROM sort_game WHERE saveSort=1")
        connection.execSQL("DROP TABLE sort_game")
        connection.execSQL("ALTER TABLE sort_game1 RENAME TO sort_game")
        connection.execSQL("CREATE TABLE IF NOT EXISTS sort_channel1 (id TEXT NOT NULL, videoSort TEXT, videoType TEXT, clipPeriod TEXT, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO sort_channel1 (id, videoSort, videoType, clipPeriod) SELECT id, videoSort, videoType, clipPeriod FROM sort_channel WHERE saveSort=1")
        connection.execSQL("DROP TABLE sort_channel")
        connection.execSQL("ALTER TABLE sort_channel1 RENAME TO sort_channel")
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS filters (id INTEGER NOT NULL, gameId TEXT, gameSlug TEXT, gameName TEXT, tags TEXT, languages TEXT, PRIMARY KEY (id))")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS recent_search (id INTEGER NOT NULL, query TEXT NOT NULL, type TEXT NOT NULL, lastSearched INTEGER NOT NULL, PRIMARY KEY (id))")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS playback_states (type TEXT, streamId TEXT, videoId TEXT, clipId TEXT, offlineVideoId INTEGER, channelId TEXT, channelLogin TEXT, channelName TEXT, channelImage TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, title TEXT, thumbnail TEXT, createdAt TEXT, viewerCount INTEGER, durationSeconds INTEGER, videoType TEXT, videoOffsetSeconds INTEGER, videoAnimatedPreviewURL TEXT, position INTEGER, paused INTEGER NOT NULL, qualities TEXT, quality TEXT, previousQuality TEXT, restoreQuality INTEGER NOT NULL, playlistUrl TEXT, restorePlaylist INTEGER NOT NULL, useCustomProxy INTEGER NOT NULL, skipAccessToken INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, bytes INTEGER NOT NULL, downloadPath TEXT, fromTime INTEGER, toTime INTEGER, status INTEGER NOT NULL, type TEXT, videoId TEXT, clipId TEXT, quality TEXT, downloadChat INTEGER NOT NULL, downloadChatEmotes INTEGER NOT NULL, chatProgress INTEGER NOT NULL, maxChatProgress INTEGER NOT NULL, chatBytes INTEGER NOT NULL, chatOffsetSeconds INTEGER NOT NULL, chatUrl TEXT, playlistToFile INTEGER NOT NULL, live INTEGER NOT NULL, lastSegmentUrl TEXT, liveCommentsArrayStarted INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, bytes, downloadPath, fromTime, toTime, status, type, videoId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, live, lastSegmentUrl, liveCommentsArrayStarted, id) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, max_progress, downloadPath, fromTime, toTime, status, type, videoId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, live, lastSegmentUrl, 0, id FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
    }
}

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS videos1 (url TEXT, source_url TEXT, source_start_position INTEGER, name TEXT, channel_id TEXT, channel_login TEXT, channel_name TEXT, channel_logo TEXT, thumbnail TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, duration INTEGER, upload_date INTEGER, download_date INTEGER, last_watch_position INTEGER, progress INTEGER NOT NULL, max_progress INTEGER NOT NULL, bytes INTEGER NOT NULL, downloadPath TEXT, fromTime INTEGER, toTime INTEGER, status INTEGER NOT NULL, type TEXT, videoId TEXT, videoCreatedAt TEXT, clipId TEXT, quality TEXT, downloadChat INTEGER NOT NULL, downloadChatEmotes INTEGER NOT NULL, chatProgress INTEGER NOT NULL, maxChatProgress INTEGER NOT NULL, chatBytes INTEGER NOT NULL, chatOffsetSeconds INTEGER NOT NULL, chatUrl TEXT, playlistToFile INTEGER NOT NULL, live INTEGER NOT NULL, lastSegmentUrl TEXT, liveCommentsArrayStarted INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO videos1 (url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, bytes, downloadPath, fromTime, toTime, status, type, videoId, clipId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, live, lastSegmentUrl, liveCommentsArrayStarted, id) SELECT url, source_url, source_start_position, name, channel_id, channel_login, channel_name, channel_logo, thumbnail, gameId, gameSlug, gameName, duration, upload_date, download_date, last_watch_position, progress, max_progress, bytes, downloadPath, fromTime, toTime, status, type, videoId, clipId, quality, downloadChat, downloadChatEmotes, chatProgress, maxChatProgress, chatBytes, chatOffsetSeconds, chatUrl, playlistToFile, live, lastSegmentUrl, liveCommentsArrayStarted, id FROM videos")
        connection.execSQL("DROP TABLE videos")
        connection.execSQL("ALTER TABLE videos1 RENAME TO videos")
        connection.execSQL("CREATE TABLE IF NOT EXISTS playback_states1 (type TEXT, streamId TEXT, videoId TEXT, clipId TEXT, offlineVideoId INTEGER, channelId TEXT, channelLogin TEXT, channelName TEXT, channelImage TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, title TEXT, thumbnail TEXT, createdAt TEXT, viewerCount INTEGER, durationSeconds INTEGER, videoType TEXT, videoOffsetSeconds INTEGER, videoCreatedAt TEXT, videoAnimatedPreviewURL TEXT, position INTEGER, paused INTEGER NOT NULL, qualities TEXT, quality TEXT, previousQuality TEXT, restoreQuality INTEGER NOT NULL, playlistUrl TEXT, restorePlaylist INTEGER NOT NULL, useCustomProxy INTEGER NOT NULL, skipAccessToken INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO playback_states1 (type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, useCustomProxy, skipAccessToken, id) SELECT type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, useCustomProxy, skipAccessToken, id FROM playback_states")
        connection.execSQL("DROP TABLE playback_states")
        connection.execSQL("ALTER TABLE playback_states1 RENAME TO playback_states")
    }
}

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS playback_states1 (type TEXT, streamId TEXT, videoId TEXT, clipId TEXT, offlineVideoId INTEGER, channelId TEXT, channelLogin TEXT, channelName TEXT, channelImage TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, title TEXT, thumbnail TEXT, createdAt TEXT, viewerCount INTEGER, durationSeconds INTEGER, videoType TEXT, videoOffsetSeconds INTEGER, videoCreatedAt TEXT, videoAnimatedPreviewURL TEXT, videoUrl TEXT, position INTEGER, paused INTEGER NOT NULL, qualities TEXT, quality TEXT, previousQuality TEXT, restoreQuality INTEGER NOT NULL, playlistUrl TEXT, restorePlaylist INTEGER NOT NULL, useCustomProxy INTEGER NOT NULL, skipAccessToken INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO playback_states1 (type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoCreatedAt, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, useCustomProxy, skipAccessToken, id) SELECT type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoCreatedAt, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, useCustomProxy, skipAccessToken, id FROM playback_states")
        connection.execSQL("DROP TABLE playback_states")
        connection.execSQL("ALTER TABLE playback_states1 RENAME TO playback_states")
    }
}

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS custom_proxies (url TEXT, addQueryParams INTEGER NOT NULL, position INTEGER NOT NULL, enabled INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
    }
}

val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS playback_states1 (type TEXT, streamId TEXT, videoId TEXT, clipId TEXT, offlineVideoId INTEGER, channelId TEXT, channelLogin TEXT, channelName TEXT, channelImage TEXT, gameId TEXT, gameSlug TEXT, gameName TEXT, title TEXT, thumbnail TEXT, createdAt TEXT, viewerCount INTEGER, durationSeconds INTEGER, videoType TEXT, videoOffsetSeconds INTEGER, videoCreatedAt TEXT, videoAnimatedPreviewURL TEXT, position INTEGER, paused INTEGER NOT NULL, qualities TEXT, quality TEXT, previousQuality TEXT, restoreQuality INTEGER NOT NULL, playlistUrl TEXT, restorePlaylist INTEGER NOT NULL, useCustomProxy INTEGER NOT NULL, currentCustomProxy INTEGER NOT NULL, useStreamProxy INTEGER NOT NULL, currentStreamProxy INTEGER NOT NULL, skipAccessToken INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
        connection.execSQL("INSERT INTO playback_states1 (type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoCreatedAt, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, useCustomProxy, currentCustomProxy, useStreamProxy, currentStreamProxy, skipAccessToken, id) SELECT type, streamId, videoId, clipId, offlineVideoId, channelId, channelLogin, channelName, channelImage, gameId, gameSlug, gameName, title, thumbnail, createdAt, viewerCount, durationSeconds, videoType, videoOffsetSeconds, videoCreatedAt, videoAnimatedPreviewURL, position, paused, qualities, quality, previousQuality, restoreQuality, playlistUrl, restorePlaylist, useCustomProxy, 0, 0, 0, skipAccessToken, id FROM playback_states")
        connection.execSQL("DROP TABLE playback_states")
        connection.execSQL("ALTER TABLE playback_states1 RENAME TO playback_states")
        connection.execSQL("CREATE TABLE IF NOT EXISTS stream_proxies (host TEXT, port INTEGER, username TEXT, password TEXT, proxyPlaybackAccessToken INTEGER NOT NULL, proxyMultivariantPlaylist INTEGER NOT NULL, proxyMediaPlaylist INTEGER NOT NULL, position INTEGER NOT NULL, enabled INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
    }
}

val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS video_swap (platform TEXT, playerType TEXT, position INTEGER NOT NULL, enabled INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (id))")
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
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_33,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
    MIGRATION_38_39,
    MIGRATION_39_40,
    MIGRATION_40_41
)
