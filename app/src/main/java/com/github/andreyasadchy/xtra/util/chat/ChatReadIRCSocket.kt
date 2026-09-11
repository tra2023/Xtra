package com.github.andreyasadchy.xtra.util.chat

import android.os.Build
import com.github.andreyasadchy.xtra.socket.IrcLineEvent
import com.github.andreyasadchy.xtra.socket.IrcRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds

class ChatReadIRCSocket(
    private val useSSL: Boolean,
    private val channelLogin: String,
    private val trustManager: Lazy<X509TrustManager>,
    private val listener: ChatReadWebSocket.Listener,
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                connect()
                var line = reader?.readLine()
                while (line != null) {
                    when (val event = IrcRouter.routeLine(line)) {
                        IrcLineEvent.Ping -> {
                            write("PONG :tmi.twitch.tv")
                            writer?.flush()
                        }
                        is IrcLineEvent.Command -> {
                            when (event.command) {
                                "PRIVMSG" -> listener.onChatMessage(event.message, false)
                                "USERNOTICE" -> listener.onChatMessage(event.message, true)
                                "CLEARMSG" -> listener.onClearMessage(event.message)
                                "CLEARCHAT" -> listener.onClearChat(event.message)
                                "NOTICE" -> listener.onNotice(event.message)
                                "ROOMSTATE" -> listener.onRoomState(event.message)
                                "USERSTATE" -> listener.onUserState(event.message)
                            }
                        }
                        IrcLineEvent.Pong, IrcLineEvent.Reconnect -> {
                        }
                    }
                    line = reader?.readLine()
                }
            } catch (e: Exception) {
                if (socket?.isClosed != true && e.message != "Connection reset" && e.message != "recvfrom failed: ECONNRESET (Connection reset by peer)") {
                    listener.onDisconnect(e.toString(), e.stackTraceToString())
                }
            }
            close()
            delay(1.seconds)
        }
    }

    private suspend fun connect() = withContext(Dispatchers.IO) {
        socket = if (useSSL) {
            val socketFactory = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> SSLSocketFactory.getDefault()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> SSLContext.getDefault().socketFactory
                else -> {
                    val sslContext = SSLContext.getInstance("TLSv1.3")
                    sslContext.init(null, arrayOf(trustManager.value), null)
                    sslContext.socketFactory
                }
            }
            socketFactory.createSocket("irc.twitch.tv", 6697)
        } else {
            Socket("irc.twitch.tv", 6667)
        }
        reader = BufferedReader(InputStreamReader(socket?.inputStream))
        writer = BufferedWriter(OutputStreamWriter(socket?.outputStream))
        IrcRouter.readConnectWrites(channelLogin, IrcRouter.readNick()).forEach {
            write(it)
        }
        writer?.flush()
        listener.onConnect()
    }

    private suspend fun write(message: String) = withContext(Dispatchers.IO) {
        writer?.write(message + System.lineSeparator())
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        job?.cancel()
        if (socket?.isClosed == false) {
            try {
                socket?.close()
            } catch (e: Exception) {

            }
        }
    }

    private suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket?.close()
        } catch (e: Exception) {

        }
    }
}
