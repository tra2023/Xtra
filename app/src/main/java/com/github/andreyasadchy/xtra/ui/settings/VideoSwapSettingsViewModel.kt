package com.github.andreyasadchy.xtra.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VideoSwapSettingsViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoSwapSettingsUiState())
    val state = _state.asStateFlow()
    private val writes = Mutex()
    private var initialized = false
    private var loaded = false

    fun initialize(platform: String?, playerType: String?) {
        val defaultItem = VideoSwapListItem(
            id = Int.MIN_VALUE,
            platform = platform,
            playerType = playerType,
            isDefault = true,
        )
        _state.value = _state.value.copy(items = listOf(defaultItem) + _state.value.items.filterNot { it.isDefault })
        if (!initialized) {
            initialized = true
            load()
        }
    }

    fun load() {
        if (writes.isLocked) return
        viewModelScope.launch {
            writes.withLock {
                _state.value = _state.value.copy(loading = true, error = null)
                try {
                    val stored = playerRepository.getVideoSwapItems().sortedBy { it.position }
                    val items = stored.map { it.toListItem() }
                    if (stored.withIndex().any { (index, item) -> item.position != index }) {
                        playerRepository.updateVideoSwapItems(items.mapIndexed { index, item -> item.toEntity(index) })
                    }
                    _state.value = _state.value.copy(items = _state.value.items.filter { it.isDefault } + items)
                    loaded = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loaded = false
                    _state.value = _state.value.copy(error = e.message ?: e.toString())
                } finally {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    fun updateDefault(platform: String, playerType: String) {
        _state.value = _state.value.copy(items = _state.value.items.map {
            if (it.isDefault) it.copy(platform = platform, playerType = playerType) else it
        })
    }

    fun add(platform: String, playerType: String) = write {
        val items = _state.value.items
        val item = VideoSwap(platform = platform, playerType = playerType, position = items.count { !it.isDefault })
        item.id = playerRepository.saveVideoSwap(item).toInt()
        _state.value = _state.value.copy(items = _state.value.items + item.toListItem())
    }

    fun edit(id: Int, platform: String, playerType: String) = update(id) { it.copy(platform = platform, playerType = playerType) }

    fun toggle(id: Int, enabled: Boolean) = update(id) { it.copy(enabled = enabled) }

    private fun update(id: Int, transform: (VideoSwapListItem) -> VideoSwapListItem) = write {
        val items = _state.value.items
        val index = items.indexOfFirst { it.id == id && !it.isDefault }
        if (index <= 0) return@write
        val item = transform(items[index])
        playerRepository.updateVideoSwap(item.toEntity(index - 1))
        _state.value = _state.value.copy(items = _state.value.items.map { if (it.id == id) item else it })
    }

    fun delete(id: Int) = write {
        val items = _state.value.items
        val index = items.indexOfFirst { it.id == id && !it.isDefault }
        if (index <= 0) return@write
        playerRepository.deleteVideoSwap(items[index].toEntity(index - 1))
        val remaining = items.filterNot { it.id == id }
        playerRepository.updateVideoSwapItems(remaining.drop(1).mapIndexed { position, item -> item.toEntity(position) })
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
    }

    fun reorder(ids: List<Int>) = write {
        val items = _state.value.items
        val custom = items.filterNot { it.isDefault }.associateBy { it.id }
        if (ids.size != custom.size || ids.toSet() != custom.keys) return@write
        if (ids == items.drop(1).map { it.id }) return@write
        val ordered = ids.map { custom.getValue(it) }
        playerRepository.updateVideoSwapItems(ordered.mapIndexed { index, item -> item.toEntity(index) })
        _state.value = _state.value.copy(items = _state.value.items.filter { it.isDefault } + ordered)
    }

    private fun write(block: suspend () -> Unit) {
        if (!loaded || _state.value.loading || _state.value.error != null) return
        viewModelScope.launch {
            writes.withLock {
                if (!loaded || _state.value.error != null) return@withLock
                _state.value = _state.value.copy(saving = true)
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = e.message ?: e.toString())
                } finally {
                    _state.value = _state.value.copy(saving = false)
                }
            }
        }
    }

    private fun VideoSwap.toListItem() = VideoSwapListItem(id = id, platform = platform, playerType = playerType, enabled = enabled)

    private fun VideoSwapListItem.toEntity(position: Int) = VideoSwap(
        platform = platform,
        playerType = playerType,
        position = position,
        enabled = enabled,
    ).also { it.id = id }

    companion object {
        val VideoSwapSettingsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                VideoSwapSettingsViewModel(xtraModule.playerRepository)
            }
        }
    }
}
