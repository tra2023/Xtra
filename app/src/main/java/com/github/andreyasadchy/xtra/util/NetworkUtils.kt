package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NetworkUtils {
    fun interface ProgressListener {
        fun update(bytesRead: Int)
    }

    class ProgressInterceptor(val progressListener: ProgressListener?): Interceptor {
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
                                        progressListener?.update(totalBytesRead.toInt())
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

    suspend fun Call.executeAsync(): Response =
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
