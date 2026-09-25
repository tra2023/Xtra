package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.browse.TagSearchController
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin Android wrapper around [TagSearchController]: only binds the shared
 * query/pager logic to [viewModelScope] with the platform settings store.
 */
class SearchTagsViewModel(
    applicationContext: Context,
    graphQLRepository: GraphQLRepository,
) : ViewModel() {

    private val controller = TagSearchController(
        scope = viewModelScope,
        settings = AndroidXtraSettings(applicationContext.prefs(), applicationContext.tokenPrefs()),
        graphQLRepository = graphQLRepository,
    )

    var getGameTags: Boolean
        get() = controller.getGameTags
        set(value) {
            controller.getGameTags = value
        }

    val query: StateFlow<String> = controller.query
    val flow: Flow<PagingData<Tag>> = controller.flow

    fun setQuery(newQuery: String) {
        controller.setQuery(newQuery)
    }

    companion object {
        val SearchTagsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                SearchTagsViewModel(application.applicationContext, xtraModule.graphQLRepository)
            }
        }
    }
}
