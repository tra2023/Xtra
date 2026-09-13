package com.github.andreyasadchy.xtra.shared.browse

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.graphql.type.Language
import com.github.andreyasadchy.xtra.graphql.type.StreamSort
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.datasource.StreamsDataSource
import com.github.andreyasadchy.xtra.shared.settings.SharedAuthHeaders
import com.github.andreyasadchy.xtra.shared.settings.XtraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Platform-agnostic port of `TopStreamsViewModel`
 * (`app/.../ui/top/TopStreamsViewModel.kt`).
 *
 * Same rules as [GamesBrowseController]: no Context, repositories + [settings]
 * injected, caller supplies the CoroutineScope (viewModelScope on Android).
 * Sort constants duplicated as plain strings so commonMain doesn't depend on
 * the Android-only `StreamsSortDialog` (`SORT_VIEWERS`, `SORT_VIEWERS_ASC`, `RECENT`).
 */
class TopStreamsBrowseController(
    scope: CoroutineScope,
    private val settings: XtraSettings,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) {
    val filter = MutableStateFlow<TopStreamsFilter?>(null)

    val sort: String
        get() = filter.value?.sort ?: SORT_VIEWERS
    val tags: Array<String>
        get() = filter.value?.tags ?: emptyArray()
    val languages: Array<String>
        get() = filter.value?.languages ?: emptyArray()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: Flow<PagingData<Stream>> = filter.flatMapLatest { _ ->
        val config = SharedAuthHeaders.loadConfig(settings)
        val compactAll = config.compactStreams == "all"
        Pager(
            if (compactAll) {
                PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
            } else {
                PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
            }
        ) {
            StreamsDataSource(
                gqlQueryLanguages = languages.ifEmpty { null }?.mapNotNull { code ->
                    Language.entries.find { it.rawValue == code }
                },
                gqlQuerySort = when (sort) {
                    SORT_VIEWERS -> StreamSort.VIEWER_COUNT
                    SORT_VIEWERS_ASC -> StreamSort.VIEWER_COUNT_ASC
                    RECENT -> StreamSort.RECENT
                    else -> StreamSort.VIEWER_COUNT
                },
                gqlLanguages = languages.ifEmpty { null }?.toList(),
                gqlSort = when (sort) {
                    SORT_VIEWERS -> "VIEWER_COUNT"
                    SORT_VIEWERS_ASC -> "VIEWER_COUNT_ASC"
                    RECENT -> "RECENT"
                    else -> "VIEWER_COUNT"
                },
                tags = tags.ifEmpty { null }?.toList(),
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                helixHeaders = SharedAuthHeaders.helixHeaders(config),
                helixRepository = helixRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }

    fun setFilter(sort: String?, tags: Array<String>?, languages: Array<String>?) {
        filter.value = TopStreamsFilter(sort, tags, languages)
    }

    class TopStreamsFilter(
        val sort: String?,
        val tags: Array<String>?,
        val languages: Array<String>?,
    )

    companion object {
        const val SORT_VIEWERS = "viewers"
        const val SORT_VIEWERS_ASC = "viewers_asc"
        const val RECENT = "recent"
    }

    init {
        requireNotNull(scope)
    }
}
