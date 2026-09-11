package com.github.andreyasadchy.xtra.util

import android.annotation.SuppressLint
import android.net.http.HttpEngine
import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.XtraHttpRequest
import com.github.andreyasadchy.xtra.repository.XtraHttpResponse
import com.github.andreyasadchy.xtra.util.NetworkUtils.executeAsync
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.UploadDataProviders
import java.util.concurrent.ExecutorService

/**
 * Android [XtraHttpClient] covering the user-selectable engines.
 * The triple-branch lives here once so migrated repositories stay engine-agnostic.
 */
class AppXtraHttpClient(
    private val httpEngine: Lazy<HttpEngine?>,
    private val cronetEngine: Lazy<CronetEngine?>,
    private val cronetExecutor: Lazy<ExecutorService>,
    private val okHttpClient: Lazy<OkHttpClient>,
) : XtraHttpClient {

    override suspend fun execute(request: XtraHttpRequest): XtraHttpResponse {
        return when {
            request.engine == C.HTTP_ENGINE && httpEngine.value != null -> executeHttpEngine(request)
            request.engine == C.CRONET && cronetEngine.value != null -> executeCronet(request)
            else -> executeOkHttp(request)
        }
    }

    @SuppressLint("NewApi")
    private suspend fun executeHttpEngine(request: XtraHttpRequest): XtraHttpResponse {
        val response = suspendCancellableCoroutine { continuation ->
            val timeout = NetworkUtils.HttpEngineTimeout()
            val urlRequest = httpEngine.value!!.newUrlRequestBuilder(
                request.url,
                cronetExecutor.value,
                NetworkUtils.ByteArrayUrlCallback(continuation, timeout)
            ).apply {
                request.headers.forEach { (key, value) -> addHeader(key, value) }
                if (request.method == XtraHttpRequest.DELETE ||
                    request.method == XtraHttpRequest.PUT ||
                    request.method == XtraHttpRequest.PATCH
                ) {
                    setHttpMethod(request.method)
                }
                request.body?.let {
                    setUploadDataProvider(NetworkUtils.ByteArrayUploadProvider(it), cronetExecutor.value)
                }
            }.build()
            timeout.start(urlRequest, continuation)
            urlRequest.start()
            continuation.invokeOnCancellation {
                urlRequest.cancel()
                timeout.stop()
            }
        }
        return XtraHttpResponse(response.info.httpStatusCode, response.body)
    }

    private suspend fun executeCronet(request: XtraHttpRequest): XtraHttpResponse {
        val response = suspendCancellableCoroutine { continuation ->
            val timeout = NetworkUtils.CronetTimeout()
            val urlRequest = cronetEngine.value!!.newUrlRequestBuilder(
                request.url,
                NetworkUtils.ByteArrayCronetCallback(continuation, timeout),
                cronetExecutor.value
            ).apply {
                request.headers.forEach { (key, value) -> addHeader(key, value) }
                if (request.method == XtraHttpRequest.DELETE ||
                    request.method == XtraHttpRequest.PUT ||
                    request.method == XtraHttpRequest.PATCH
                ) {
                    setHttpMethod(request.method)
                }
                request.body?.let {
                    setUploadDataProvider(UploadDataProviders.create(it), cronetExecutor.value)
                }
            }.build()
            timeout.start(urlRequest, continuation)
            urlRequest.start()
            continuation.invokeOnCancellation {
                urlRequest.cancel()
                timeout.stop()
            }
        }
        return XtraHttpResponse(response.info.httpStatusCode, response.body)
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
        return okHttpClient.value.newCall(builder.build()).executeAsync().use { response ->
            XtraHttpResponse(response.code, response.body.bytes())
        }
    }
}
