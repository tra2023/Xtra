package com.github.andreyasadchy.xtra.repository.browse

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.TagsDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Platform-agnostic port of `SearchTagsViewModel` (`app/.../ui/common/SearchTagsViewModel.kt`).
 *
 * Differences from the Android version:
 * - No `Context` / `XtraApp` factory. Takes [settings] + repositories directly.
 * - No `androidx.lifecycle.ViewModel` base (so it compiles on JVM desktop without
 *   lifecycle artifacts). Android wraps it: `SearchTagsViewModel` delegates its
 *   `query`/`flow` to this controller using `viewModelScope`; desktop passes
 *   its own scope.
 * - Paging setup identical: pageSize 30, prefetch 10, same `TagsDataSource`.
 */
class TagSearchController(
    scope: CoroutineScope,
    private val settings: XtraSettings,
    private val graphQLRepository: GraphQLRepository,
) {
    var getGameTags = false

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: Flow<PagingData<Tag>> = _query.flatMapLatest { query ->
        val config = SharedAuthHeaders.loadConfig(settings)
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        ) {
            TagsDataSource(
                getGameTags = getGameTags,
                query = query,
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }.cachedIn(scope)

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
        }
    }
}
