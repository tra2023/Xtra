package com.github.andreyasadchy.xtra.ui.game.clips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.graphql.type.ClipsPeriod
import com.github.andreyasadchy.xtra.graphql.type.Language
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.VideosSort
import com.github.andreyasadchy.xtra.repository.GameSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.GameClipsDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Game clips tab. Takes [settings] and the navigation arguments as plain values, so it carries no
 * Context and no generated `*FragmentArgs` reference; the Android factory reads the arguments.
 */
class GameClipsViewModel(
    private val settings: XtraSettings,
    private val gameId: String?,
    private val gameSlug: String?,
    private val gameName: String?,
    private val gameSortRepository: GameSortRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)
    val filtersText = MutableStateFlow<CharSequence?>(null)

    val period: String
        get() = filter.value?.period ?: VideosSort.PERIOD_WEEK
    val languages: Array<String>
        get() = filter.value?.languages ?: emptyArray()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        val config = SharedAuthHeaders.loadConfig(settings)
        Pager(
            PagingConfig(pageSize = 20, prefetchDistance = 3, initialLoadSize = 20)
        ) {
            val started = when (period) {
                VideosSort.PERIOD_ALL -> null
                else -> {
                    val days = when (period) {
                        VideosSort.PERIOD_DAY -> 1
                        VideosSort.PERIOD_WEEK -> 7
                        VideosSort.PERIOD_MONTH -> 30
                        else -> 7
                    }
                    (Clock.System.now() - days.days).toString()
                }
            }
            val ended = when (period) {
                VideosSort.PERIOD_ALL -> null
                else -> Clock.System.now().toString()
            }
            val gqlQueryPeriod = when (period) {
                VideosSort.PERIOD_DAY -> ClipsPeriod.LAST_DAY
                VideosSort.PERIOD_WEEK -> ClipsPeriod.LAST_WEEK
                VideosSort.PERIOD_MONTH -> ClipsPeriod.LAST_MONTH
                VideosSort.PERIOD_ALL -> ClipsPeriod.ALL_TIME
                else -> ClipsPeriod.LAST_WEEK
            }
            val gqlPeriod = when (period) {
                VideosSort.PERIOD_DAY -> "LAST_DAY"
                VideosSort.PERIOD_WEEK -> "LAST_WEEK"
                VideosSort.PERIOD_MONTH -> "LAST_MONTH"
                VideosSort.PERIOD_ALL -> "ALL_TIME"
                else -> "LAST_WEEK"
            }
            GameClipsDataSource(
                gameId = gameId,
                gameSlug = gameSlug,
                gameName = gameName,
                gqlQueryLanguages = languages.ifEmpty { null }?.mapNotNull { language ->
                    Language.entries.find { it.rawValue == language }
                },
                gqlQueryPeriod = gqlQueryPeriod,
                gqlLanguages = languages.ifEmpty { null }?.toList(),
                gqlPeriod = gqlPeriod,
                startedAt = started,
                endedAt = ended,
                helixHeaders = SharedAuthHeaders.helixHeaders(config),
                helixRepository = helixRepository,
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getGameSort(id: String): GameSort? {
        return gameSortRepository.getById(id)
    }

    suspend fun saveGameSort(item: GameSort) {
        gameSortRepository.save(item)
    }

    suspend fun deleteGameSort(item: GameSort) {
        gameSortRepository.delete(item)
    }

    fun setFilter(period: String?, languages: Array<String>?) {
        filter.value = Filter(period, languages)
    }

    class Filter(
        val period: String?,
        val languages: Array<String>?,
    )
}
