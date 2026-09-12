package com.github.andreyasadchy.xtra

import android.app.Application
import android.os.Build
import com.github.andreyasadchy.xtra.db.getDatabaseBuilder
import com.github.andreyasadchy.xtra.db.getRoomDatabase
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.ChannelSortRepository
import com.github.andreyasadchy.xtra.repository.GameSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.LocalGameFollowsRepository
import com.github.andreyasadchy.xtra.repository.NotificationsRepository
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.RecentSearchesRepository
import com.github.andreyasadchy.xtra.repository.SavedFiltersRepository
import com.github.andreyasadchy.xtra.util.AppXtraHttpClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class XtraModule(application: Application) {

    val okHttpClient = lazy {
        OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                val sslContext = SSLContext.getInstance("TLSv1.3")
                sslContext.init(null, arrayOf(trustManager.value), null)
                sslSocketFactory(sslContext.socketFactory, trustManager.value)
            }
        }.build()
    }

    val trustManager = lazy {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        var count = 0
        val certificateFactory = CertificateFactory.getInstance("X.509")
        application.resources.openRawResource(R.raw.isrgrootx1).use {
            val certificate = certificateFactory.generateCertificate(it)
            keyStore.setCertificateEntry("cert_0", certificate)
            count += 1
        }
        val defaultTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        defaultTrustManagerFactory.init(null as KeyStore?)
        val defaultTrustManager = defaultTrustManagerFactory.trustManagers.first() as X509TrustManager
        defaultTrustManager.acceptedIssuers.forEach {
            keyStore.setCertificateEntry("cert_$count", it)
            count += 1
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        trustManagerFactory.trustManagers.first() as X509TrustManager
    }

    val json by lazy {
        Json { ignoreUnknownKeys = true }
    }

    val database by lazy {
        getRoomDatabase(getDatabaseBuilder(application))
    }

    val xtraHttpClient by lazy {
        AppXtraHttpClient(okHttpClient)
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
        OfflineVideosRepository(database.offlineVideos(), database.bookmarks())
    }

    val playerRepository by lazy {
        PlayerRepository(okHttpClient, json, database.recentEmotes(), database.videoSwap(), database.videoPositions(), database.playbackStates(), graphQLRepository, helixRepository)
    }

    val recentSearchesRepository by lazy {
        RecentSearchesRepository(database.recentSearches())
    }

    val savedFiltersRepository by lazy {
        SavedFiltersRepository(database.savedFilters())
    }
}