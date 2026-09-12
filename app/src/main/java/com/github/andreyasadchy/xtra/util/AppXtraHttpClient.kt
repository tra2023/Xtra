package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.XtraHttpRequest
import com.github.andreyasadchy.xtra.repository.XtraHttpResponse
import com.github.andreyasadchy.xtra.util.NetworkUtils.executeAsync
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Android [XtraHttpClient] using OkHttp.
 */
class AppXtraHttpClient(
    private val okHttpClient: Lazy<OkHttpClient>,
) : XtraHttpClient {

    override suspend fun execute(request: XtraHttpRequest): XtraHttpResponse {
        return executeOkHttp(request)
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
}
