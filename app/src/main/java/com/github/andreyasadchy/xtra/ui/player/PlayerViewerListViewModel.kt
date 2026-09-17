package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewerListViewModel(
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()

    private val _viewerList = MutableStateFlow<ChannelViewerList?>(null)
    val viewerList: StateFlow<ChannelViewerList?> = _viewerList
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    fun loadViewerList(channelLogin: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (_viewerList.value != null || _loading.value) return
        _loading.value = true
        _failed.value = false
        viewModelScope.launch {
            var needsIntegrity = false
            try {
                try {
                    val response = graphQLRepository.loadQueryUserChatters(gqlHeaders, login = channelLogin)
                    if (enableIntegrity && response.errors?.any { it.message == C.FAILED_INTEGRITY_CHECK } == true) {
                        needsIntegrity = true
                    } else {
                        _viewerList.value = response.data?.user?.channel?.chatters?.let { chatters ->
                            ChannelViewerList(
                                broadcasters = chatters.broadcasters?.mapNotNull { it.login } ?: emptyList(),
                                moderators = chatters.moderators?.mapNotNull { it.login } ?: emptyList(),
                                vips = chatters.vips?.mapNotNull { it.login } ?: emptyList(),
                                viewers = chatters.viewers?.mapNotNull { it.login } ?: emptyList(),
                                count = chatters.count,
                            )
                        }
                        _failed.value = _viewerList.value == null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val response = graphQLRepository.loadChannelViewerList(gqlHeaders, channelLogin)
                    if (enableIntegrity && response.errors?.any { it.message == C.FAILED_INTEGRITY_CHECK } == true) {
                        needsIntegrity = true
                    } else {
                        _viewerList.value = response.data?.user?.channel?.chatters?.let { chatters ->
                            ChannelViewerList(
                                broadcasters = chatters.broadcasters?.mapNotNull { it.login } ?: emptyList(),
                                moderators = chatters.moderators?.mapNotNull { it.login } ?: emptyList(),
                                vips = chatters.vips?.mapNotNull { it.login } ?: emptyList(),
                                viewers = chatters.viewers?.mapNotNull { it.login } ?: emptyList(),
                                count = chatters.count,
                            )
                        }
                        _failed.value = _viewerList.value == null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failed.value = true
            } finally {
                _loading.value = false
            }
            if (needsIntegrity) {
                _failed.value = true
                integrity.emit("refresh")
            }
        }
    }

    companion object {
        val PlayerViewerListViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                PlayerViewerListViewModel(xtraModule.graphQLRepository)
            }
        }
    }
}
