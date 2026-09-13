package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.socket.HermesEvent
import com.github.andreyasadchy.xtra.socket.HermesRouter
import com.github.andreyasadchy.xtra.socket.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class HermesWebSocket(
    private val channelId: String,
    private val userId: String?,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val collectPoints: Boolean,
    private val showRaids: Boolean,
    private val showPolls: Boolean,
    private val showPredictions: Boolean,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var pongTimer: Timer? = null
    private var timeout = 15000L
    private var minuteWatchedTimer: Timer? = null
    private val router = HermesRouter()

    fun connect(coroutineScope: CoroutineScope): Job {
        scope = coroutineScope
        webSocket = WebSocket("wss://hermes.twitch.tv/v1?clientId=${gqlClientId}", WebSocketListener())
        return coroutineScope.launch(Dispatchers.IO) {
            webSocket?.start()
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        pongTimer?.cancel()
        minuteWatchedTimer?.cancel()
        minuteWatchedTimer = null
        job?.cancel()
        webSocket?.disconnect()
        webSocket?.close()
        webSocket = null
        scope = null
    }

    private suspend fun startPongTimer() = withContext(Dispatchers.IO) {
        pongTimer = Timer().apply {
            schedule(timeout) {
                scope?.launch {
                    webSocket?.disconnect()
                }
            }
        }
    }

    private suspend fun startMinuteWatchedTimer() = withContext(Dispatchers.IO) {
        minuteWatchedTimer = Timer().apply {
            scheduleAtFixedRate(60000, 60000) {
                scope?.launch {
                    listener.onMinuteWatched()
                }
            }
        }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onPlaybackMessage(message: String) {}
        suspend fun onStreamInfo(message: String) {}
        suspend fun onRewardMessage(message: String) {}
        suspend fun onPointsEarned(message: String) {}
        suspend fun onClaimAvailable() {}
        suspend fun onMinuteWatched() {}
        suspend fun onRaidUpdate(message: String, openStream: Boolean) {}
        suspend fun onPollUpdate(message: String) {}
        suspend fun onPredictionUpdate(message: String) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            listener.onConnect()
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            try {
                when (val event = router.route(message)) {
                    is HermesEvent.Playback -> listener.onPlaybackMessage(event.messageJson)
                    is HermesEvent.StreamInfo -> listener.onStreamInfo(event.messageJson)
                    is HermesEvent.Reward -> listener.onRewardMessage(event.messageJson)
                    is HermesEvent.PointsEarned -> listener.onPointsEarned(event.messageJson)
                    HermesEvent.ClaimAvailable -> listener.onClaimAvailable()
                    is HermesEvent.Raid -> listener.onRaidUpdate(event.messageJson, event.openStream)
                    is HermesEvent.Poll -> listener.onPollUpdate(event.messageJson)
                    is HermesEvent.Prediction -> listener.onPredictionUpdate(event.messageJson)
                    HermesEvent.Keepalive -> {
                        pongTimer?.cancel()
                        startPongTimer()
                    }
                    HermesEvent.Reconnect -> {
                        pongTimer?.cancel()
                        webSocket.disconnect()
                    }
                    is HermesEvent.Welcome -> {
                        event.keepaliveSec?.takeIf { it > 0 }?.let {
                            timeout = it * 1000L
                        }
                        pongTimer?.cancel()
                        startPongTimer()
                        router.buildAuthenticate(userId, gqlToken, collectPoints)?.let {
                            webSocket.write(it)
                        }
                        router.buildSubscriptions(channelId, userId, gqlToken, collectPoints, showRaids, showPolls, showPredictions).messages.forEach {
                            webSocket.write(it)
                        }
                        if (collectPoints && !userId.isNullOrBlank() && !gqlToken.isNullOrBlank() && minuteWatchedTimer == null) {
                            startMinuteWatchedTimer()
                        }
                    }
                    HermesEvent.Ignore -> {
                    }
                }
            } catch (e: Exception) {

            }
        }

        override suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String?) {
            listener.onDisconnect(message, fullMsg)
        }
    }
}
