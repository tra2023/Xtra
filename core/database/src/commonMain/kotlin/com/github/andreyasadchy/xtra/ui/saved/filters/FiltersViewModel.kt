package com.github.andreyasadchy.xtra.ui.saved.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.repository.SavedFiltersRepository
import kotlinx.coroutines.launch

/**
 * Paged list of the user's saved filters. Lives in `:core:database` because the paging source is the
 * [SavedFiltersRepository]; the Android host keeps its own `ViewModelProvider.Factory`.
 */
class FiltersViewModel(
    private val savedFiltersRepository: SavedFiltersRepository,
) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        savedFiltersRepository.getAll()
    }.flow.cachedIn(viewModelScope)

    fun delete(item: SavedFilter) {
        viewModelScope.launch {
            savedFiltersRepository.delete(item)
        }
    }
}
