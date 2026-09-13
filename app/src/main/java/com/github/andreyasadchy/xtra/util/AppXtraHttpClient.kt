package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.XtraHttpRequest
import com.github.andreyasadchy.xtra.repository.XtraHttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Android [XtraHttpClient] using OkHttp.
 */
class AppXtraHttpClient(
    private val okHttpClient: Lazy<OkHttpClient>,
) : XtraHttpClient {

    override suspend fun execute(request: XtraHttpRequest): XtraHttpResponse {
        return executeOkHttp(request)
    }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Long?,
        onProgress: (bytesRead: Long, contentLength: Long?) -> Unit,
    ): ByteArray {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.header(key, value) }
        val client = okHttpClient.value.newBuilder().apply {
            addNetworkInterceptor(ProgressInterceptor { bytesRead, contentLength ->
                onProgress(bytesRead, contentLength)
            })
        }.build()
        return client.newCall(builder.build()).executeAsync().use { response ->
            if (response.isSuccessful) {
                response.body.bytes()
            } else {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
        }
    }

    private suspend fun executeOkHttp(request: XtraHttpRequest): XtraHttpResponse {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (key, value) -> builder.header(key, value) }
        val body = request.body
        when (request.method) {
            XtraHttpRequest.POST -> builder.post(requireNotNull(body).toRequestBody())
            XtraHttpRequest.DELETE -> builder.method("DELETE", null)
            XtraHttpRequest.PUT -> builder.method("PUT", null)
            XtraHttpRequest.PATCH -> builder.method("PATCH", requireNotNull(body).toRequestBody())
        }
        val timeoutMs = request.timeoutMs
        val client = if (timeoutMs != null) {
            okHttpClient.value.newBuilder().apply {
                connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            }.build()
        } else {
            okHttpClient.value
        }
        return client.newCall(builder.build()).executeAsync().use { response ->
            XtraHttpResponse(response.code, response.body.bytes())
        }
    }

    private class ProgressInterceptor(
        val progressListener: (bytesRead: Long, contentLength: Long?) -> Unit,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            return response.newBuilder().apply {
                body(
                    object : ResponseBody() {
                        private var bufferedSource: BufferedSource? = null

                        override fun contentType() = response.body.contentType()

                        override fun contentLength() = response.body.contentLength()

                        override fun source(): BufferedSource {
                            return bufferedSource ?: object : ForwardingSource(response.body.source()) {
                                private var totalBytesRead = 0L

                                override fun read(sink: Buffer, byteCount: Long): Long {
                                    val bytesRead = super.read(sink, byteCount)
                                    if (bytesRead != -1L) {
                                        totalBytesRead += bytesRead
                                        progressListener(totalBytesRead, response.body.contentLength().takeIf { it >= 0 })
                                    }
                                    return bytesRead
                                }
                            }.buffer().also { bufferedSource = it }
                        }
                    }
                )
            }.build()
        }
    }

    private suspend fun Call.executeAsync(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                this.cancel()
            }
            this.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: okio.IOException,
                    ) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        continuation.resume(response) { _, value, _ ->
                            value.closeQuietly()
                        }
                    }
                },
            )
        }
}
