package com.github.andreyasadchy.xtra

import android.app.Application
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import com.github.andreyasadchy.xtra.db.AppDatabase
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider
import org.chromium.net.QuicOptions
import org.chromium.net.RequestFinishedInfo
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class XtraModule(application: Application) {

    val httpEngine = lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
            HttpEngine.Builder(application).apply {
                addQuicHint("gql.twitch.tv", 443, 443)
                addQuicHint("www.twitch.tv", 443, 443)
                addQuicHint("7tv.io", 443, 443)
                addQuicHint("cdn.7tv.app", 443, 443)
                addQuicHint("api.betterttv.net", 443, 443)
            }.build()
        } else {
            null
        }
    }

    val cronetEngine = lazy {
        if (CronetProvider.getAllProviders(application).any { it.isEnabled }) {
            CronetEngine.Builder(application).apply {
                val userAgent = "Cronet/" + defaultUserAgent.substringAfter("Cronet/", "").substringBefore(')')
                setUserAgent(userAgent)
                @QuicOptions.Experimental
                setQuicOptions(QuicOptions.builder().setHandshakeUserAgent(userAgent).build())
                addQuicHint("gql.twitch.tv", 443, 443)
                addQuicHint("www.twitch.tv", 443, 443)
                addQuicHint("7tv.io", 443, 443)
                addQuicHint("cdn.7tv.app", 443, 443)
                addQuicHint("api.betterttv.net", 443, 443)
            }.build().also {
                if (BuildConfig.DEBUG) {
                    it.addRequestFinishedListener(object : RequestFinishedInfo.Listener(Executors.newSingleThreadExecutor()) {
                        override fun onRequestFinished(requestInfo: RequestFinishedInfo) {
                            requestInfo.responseInfo?.let {
                                Log.i("Cronet", "${it.httpStatusCode} ${it.negotiatedProtocol} ${it.url}")
                                it.allHeadersAsList?.forEach {
                                    Log.i("Cronet", "${it.key}: ${it.value}")
                                }
                            }
                        }
                    })
                }
            }
        } else {
            null
        }
    }

    val cronetExecutor = lazy {
        Executors.newCachedThreadPool()
    }

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

    val authRepository by lazy {
        AuthRepository(httpEngine, cronetEngine, cronetExecutor, okHttpClient, json)
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
        GraphQLRepository(httpEngine, cronetEngine, cronetExecutor, okHttpClient, json)
    }

    val helixRepository by lazy {
        HelixRepository(httpEngine, cronetEngine, cronetExecutor, okHttpClient, json)
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
        PlayerRepository(httpEngine, cronetEngine, cronetExecutor, okHttpClient, json, database.recentEmotes(), database.translatedChannels(), database.customProxies(), database.streamProxies(), database.videoSwap(), database.videoPositions(), database.playbackStates(), graphQLRepository, helixRepository)
    }

    val recentSearchesRepository by lazy {
        RecentSearchesRepository(database.recentSearches())
    }

    val savedFiltersRepository by lazy {
        SavedFiltersRepository(database.savedFilters())
    }
}