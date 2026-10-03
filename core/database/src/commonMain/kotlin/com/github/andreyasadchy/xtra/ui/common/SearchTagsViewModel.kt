package com.github.andreyasadchy.xtra.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.browse.TagSearchController
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around [TagSearchController]: binds the shared query/pager logic to [viewModelScope]
 * with the platform settings store, so the tag picker needs no Context.
 */
class SearchTagsViewModel(
    settings: XtraSettings,
    graphQLRepository: GraphQLRepository,
) : ViewModel() {

    private val controller = TagSearchController(
        scope = viewModelScope,
        settings = settings,
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
}
