package com.github.andreyasadchy.xtra.ui.top

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.repository.GameSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.SavedFiltersRepository
import com.github.andreyasadchy.xtra.repository.browse.TopStreamsBrowseController
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android shell around the shared [TopStreamsBrowseController]: the paging logic lives in
 * `:core:database`. The sort/filter repositories stay here because they are only used by the
 * dialog plumbing in `TopStreamsFragment`.
 */
class TopStreamsViewModel(
    applicationContext: Context,
    private val gameSortRepository: GameSortRepository,
    private val savedFiltersRepository: SavedFiltersRepository,
    graphQLRepository: GraphQLRepository,
    helixRepository: HelixRepository,
) : ViewModel() {

    private val controller = TopStreamsBrowseController(
        scope = viewModelScope,
        settings = AndroidXtraSettings(applicationContext.prefs(), applicationContext.tokenPrefs()),
        graphQLRepository = graphQLRepository,
        helixRepository = helixRepository,
    )

    val flow = controller.flow

    val filter: MutableStateFlow<TopStreamsBrowseController.TopStreamsFilter?>
        get() = controller.filter

    val sortText = MutableStateFlow<CharSequence?>(null)
    val filtersText = MutableStateFlow<CharSequence?>(null)

    val sort: String
        get() = controller.sort
    val tags: Array<String>
        get() = controller.tags
    val languages: Array<String>
        get() = controller.languages

    suspend fun getGameSort(id: String): GameSort? {
        return gameSortRepository.getById(id)
    }

    suspend fun saveGameSort(item: GameSort) {
        gameSortRepository.save(item)
    }

    suspend fun saveFilters(item: SavedFilter) {
        savedFiltersRepository.save(item)
    }

    fun setFilter(sort: String?, tags: Array<String>?, languages: Array<String>?) {
        controller.setFilter(sort, tags, languages)
    }

    companion object {
        val TopStreamsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                TopStreamsViewModel(application.applicationContext, xtraModule.gameSortRepository, xtraModule.savedFiltersRepository, xtraModule.graphQLRepository, xtraModule.helixRepository)
            }
        }
    }
}
