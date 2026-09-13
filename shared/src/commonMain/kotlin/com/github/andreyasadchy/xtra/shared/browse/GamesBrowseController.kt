package com.github.andreyasadchy.xtra.shared.browse

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.datasource.GamesDataSource
import com.github.andreyasadchy.xtra.shared.settings.SharedAuthHeaders
import com.github.andreyasadchy.xtra.shared.settings.XtraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Platform-agnostic port of `GamesViewModel` (`app/.../ui/games/GamesViewModel.kt`).
 *
 * Differences from the Android version:
 * - No `Context` / `XtraApp` factory. Takes [settings] + repositories directly.
 * - No `androidx.lifecycle.ViewModel` base (so it compiles on JVM desktop without
 *   lifecycle artifacts). Android wraps it: `GamesViewModel` delegates its `flow`
 *   to this controller using `viewModelScope`; desktop passes its own scope.
 * - Paging setup identical: pageSize 30, prefetch 10, same `GamesDataSource`.
 */
class GamesBrowseController(
    scope: CoroutineScope,
    private val settings: XtraSettings,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) {
    val filter = MutableStateFlow<GamesFilter?>(null)

    val tags: Array<Tag>
        get() = filter.value?.tags ?: emptyArray()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: Flow<PagingData<Game>> = filter.flatMapLatest { _ ->
        val config = SharedAuthHeaders.loadConfig(settings)
        Pager(PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)) {
            GamesDataSource(
                tags = tags.ifEmpty { null }?.mapNotNull { it.id },
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                helixHeaders = SharedAuthHeaders.helixHeaders(config),
                helixRepository = helixRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }

    fun setFilter(tags: Array<Tag>?) {
        filter.value = GamesFilter(tags)
    }

    class GamesFilter(val tags: Array<Tag>?)

    // Keep scope referenced so callers pass viewModelScope / desktop scope.
    init {
        requireNotNull(scope)
    }
}
