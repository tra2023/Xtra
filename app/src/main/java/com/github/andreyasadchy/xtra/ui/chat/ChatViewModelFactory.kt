package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.chat.ChatSystemStrings
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/**
 * Android factory for the shared [ChatViewModel] (`:core:database` androidMain). It owns the
 * platform-specific inputs the view model cannot reach: the localized system notices, the cache
 * directory for the cached emote responses, the content resolver used to read chat replay files,
 * and the app version used as the third-party emote User-Agent.
 */
val ChatViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        val settings = application.xtraSettings()
        ChatViewModel(
            settings = settings,
            authConfig = SharedAuthHeaders.loadConfig(settings),
            strings = application.chatSystemStrings(),
            cacheDir = application.cacheDir.absolutePath,
            userAgent = "Xtra/" + BuildConfig.VERSION_NAME,
            openChatFile = { uri -> if (uri.scheme == "content") application.contentResolver.openInputStream(uri) else null },
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            playerRepository = xtraModule.playerRepository,
            json = xtraModule.json,
        )
    }
}

/**
 * Android resources backing [ChatSystemStrings]. Formats use positional arguments, so the
 * `String.format`-style calls the view model makes line up with the resource placeholders.
 */
fun Context.chatSystemStrings(): ChatSystemStrings = ChatSystemStrings(
    loadedCachedStvEmotes = getString(R.string.loaded_cached_stv_emotes),
    loadedCachedBttvEmotes = getString(R.string.loaded_cached_bttv_emotes),
    loadedCachedFfzEmotes = getString(R.string.loaded_cached_ffz_emotes),
    clearedMessage = { userName, message -> getString(R.string.chat_clearmsg).format(userName, message) },
    disconnected = getString(R.string.disconnected),
    joinedChannel = { channelLogin -> getString(R.string.chat_join).format(channelLogin) },
    disconnectedFromChannel = { channelLogin, message -> getString(R.string.chat_disconnect).format(channelLogin, message) },
    websocketConnected = { socketName -> getString(R.string.websocket_connected).format(socketName) },
    websocketDisconnected = { socketName, message -> getString(R.string.websocket_disconnected).format(socketName, message) },
    chatCleared = getString(R.string.chat_clear),
    streamLive = { channelLogin -> getString(R.string.stream_live).format(channelLogin) },
    streamOffline = { channelLogin -> getString(R.string.stream_offline).format(channelLogin) },
    pointsEarned = { points -> getString(R.string.points_earned).format(points) },
    chatTimeout = getString(R.string.chat_timeout),
    chatBan = getString(R.string.chat_ban),
    days = getString(R.string.days),
    hours = getString(R.string.hours),
    minutes = getString(R.string.minutes),
    seconds = getString(R.string.seconds),
)
