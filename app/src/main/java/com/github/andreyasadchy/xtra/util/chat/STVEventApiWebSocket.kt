package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.socket.StvEvent
import com.github.andreyasadchy.xtra.socket.StvRouter
import com.github.andreyasadchy.xtra.socket.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class STVEventApiWebSocket(
    private val channelId: String,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null

    fun connect(coroutineScope: CoroutineScope): Job {
        webSocket = WebSocket(
            url = "wss://events.7tv.io/v3",
            listener = WebSocketListener(),
            headers = mapOf("User-Agent" to "Xtra/" + BuildConfig.VERSION_NAME)
        )
        return coroutineScope.launch(Dispatchers.IO) {
            webSocket?.start()
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        job?.cancel()
        webSocket?.disconnect()
        webSocket?.close()
        webSocket = null
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onEmoteSetUpdate(body: String) {}
        suspend fun onCosmetic(body: String) {}
        suspend fun onEntitlement(body: String) {}
        suspend fun onUpdatePresence(sessionId: String) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            StvRouter.buildSubscribes(channelId).forEach {
                webSocket.write(it)
            }
            listener.onConnect()
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            try {
                when (val event = StvRouter.route(message)) {
                    is StvEvent.EmoteSetUpdate -> listener.onEmoteSetUpdate(event.bodyJson)
                    is StvEvent.Cosmetic -> listener.onCosmetic(event.bodyJson)
                    is StvEvent.Entitlement -> listener.onEntitlement(event.bodyJson)
                    is StvEvent.Hello -> {
                        event.sessionId?.takeIf { it.isNotBlank() }?.let {
                            listener.onUpdatePresence(it)
                        }
                    }
                    StvEvent.Reconnect -> {
                        webSocket.disconnect()
                    }
                    StvEvent.Ignore -> {
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
