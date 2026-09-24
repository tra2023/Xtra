package com.github.andreyasadchy.xtra

import android.app.Application
import com.github.andreyasadchy.xtra.db.getDatabaseBuilder
import com.github.andreyasadchy.xtra.db.getRoomDatabase
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.ChannelSortRepository
import com.github.andreyasadchy.xtra.repository.GameSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.KtorXtraHttpClient
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.LocalGameFollowsRepository
import com.github.andreyasadchy.xtra.repository.NotificationsRepository
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.repository.PlaybackPositionSaver
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.RecentSearchesRepository
import com.github.andreyasadchy.xtra.repository.SavedFiltersRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class XtraModule(application: Application) {

    val okHttpClient = lazy {
        OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }.build()
    }

    val json by lazy {
        Json { ignoreUnknownKeys = true }
    }

    val database by lazy {
        getRoomDatabase(getDatabaseBuilder(application))
    }

    val ktorHttpClient by lazy {
        HttpClient(CIO)
    }

    val xtraHttpClient by lazy {
        KtorXtraHttpClient(ktorHttpClient)
    }

    val authRepository by lazy {
        AuthRepository(xtraHttpClient, json)
    }

    val bookmarksRepository by lazy {
        BookmarksRepository(database.bookmarks(), database.bookmarkIgnoredUsers(), database.offlineVideos(), deleteImage = { java.io.File(it).delete() })
    }

    val channelSortRepository by lazy {
        ChannelSortRepository(database.channelSort())
    }

    val gameSortRepository by lazy {
        GameSortRepository(database.gameSort())
    }

    val graphQLRepository by lazy {
        GraphQLRepository(xtraHttpClient, json)
    }

    val helixRepository by lazy {
        HelixRepository(xtraHttpClient, json)
    }

    val localChannelFollowsRepository by lazy {
        LocalChannelFollowsRepository(database.localChannelFollows(), database.offlineVideos(), database.bookmarks(), deleteImage = { java.io.File(it).delete() })
    }

    val localGameFollowsRepository by lazy {
        LocalGameFollowsRepository(database.localGameFollows(), deleteImage = { java.io.File(it).delete() })
    }

    val notificationsRepository by lazy {
        NotificationsRepository(database.shownNotifications(), database.notificationUsers(), graphQLRepository, helixRepository)
    }

    val offlineVideosRepository by lazy {
        OfflineVideosRepository(database.offlineVideos(), database.bookmarks(), deleteImage = { java.io.File(it).delete() })
    }

    val playerRepository by lazy {
        PlayerRepository(xtraHttpClient, json, "Xtra/" + BuildConfig.VERSION_NAME, database.recentEmotes(), database.videoSwap(), database.videoPositions(), database.playbackStates(), graphQLRepository, helixRepository)
    }

    val playbackPositionSaver by lazy {
        PlaybackPositionSaver(playerRepository, offlineVideosRepository)
    }

    val recentSearchesRepository by lazy {
        RecentSearchesRepository(database.recentSearches())
    }

    val savedFiltersRepository by lazy {
        SavedFiltersRepository(database.savedFilters())
    }
}