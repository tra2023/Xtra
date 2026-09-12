package com.github.andreyasadchy.xtra.ui.settings

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.NetworkUtils.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
class CustomProxySettingsViewModel(
    private val playerRepository: PlayerRepository,
    private val okHttpClient: Lazy<OkHttpClient>,
    private val json: Json,
) : ViewModel() {

    val list = MutableStateFlow<MutableList<CustomProxy>>(mutableListOf())
    val statusMap = mutableMapOf<String, Boolean>()
    val statusChanged = MutableSharedFlow<String>()
    var loaded = false

    fun getProxies() {
        viewModelScope.launch {
            list.value = playerRepository.getCustomProxies().sortedBy { it.position }.toMutableList()
            if (!loaded) {
                loaded = true
                val requestSemaphore = Semaphore(10)
                list.value.forEach { proxy ->
                    requestSemaphore.acquire()
                    val url = proxy.url
                    viewModelScope.launch(Dispatchers.IO) {
                        getProxyStatus(url)
                    }.also {
                        it.invokeOnCompletion {
                            requestSemaphore.release()
                        }
                    }
                }
            }
        }
    }

    fun updateProxies() {
        val items = list.value.onEachIndexed { index, proxy ->
            proxy.position = index
        }
        viewModelScope.launch {
            playerRepository.updateCustomProxies(items)
        }
    }

    fun saveProxy(item: CustomProxy) {
        viewModelScope.launch {
            val id = playerRepository.saveCustomProxy(item)
            item.id = id.toInt()
        }
    }

    fun deleteProxy(item: CustomProxy) {
        viewModelScope.launch {
            playerRepository.deleteCustomProxy(item)
            updateProxies()
        }
    }

    fun updateProxy(item: CustomProxy) {
        viewModelScope.launch {
            playerRepository.updateCustomProxy(item)
        }
    }

    fun updateProxyStatus(url: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            getProxyStatus(url)
        }
    }

    private suspend fun getProxyStatus(proxyUrl: String?) = withContext(Dispatchers.IO) {
        if (!proxyUrl.isNullOrBlank()) {
            val url = (proxyUrl.toUri().takeIf { it.host != null } ?: "https://$proxyUrl".toUri()).buildUpon().apply {
                path("ping")
            }.build().toString()
            val online = try {
                val response = okHttpClient.value.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
                            json.decodeFromString<JsonObject>(response.body.string())
                        }
                // as.luminous.dev returns {"online": false} even when it's working
                response.getValue("online").jsonPrimitive.booleanOrNull != null
            } catch (e: Exception) {
                false
            }
            statusMap[proxyUrl] = online
            statusChanged.emit(proxyUrl)
        }
    }

    companion object {
        val CustomProxySettingsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                CustomProxySettingsViewModel(xtraModule.playerRepository, xtraModule.okHttpClient, xtraModule.json)
            }
        }
    }
}
