package com.github.andreyasadchy.xtra.ui.channel.clips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.graphql.type.ClipsPeriod
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.VideosSort
import com.github.andreyasadchy.xtra.repository.ChannelSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.ChannelClipsDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Channel clips tab. Takes [settings] and the navigation arguments as plain values, so it carries no
 * Context and no generated `*FragmentArgs` reference; the Android factory reads the arguments.
 */
class ChannelClipsViewModel(
    private val settings: XtraSettings,
    private val channelId: String?,
    private val channelLogin: String?,
    private val channelSortRepository: ChannelSortRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val period: String
        get() = filter.value?.period ?: VideosSort.PERIOD_WEEK

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
            ChannelClipsDataSource(
                channelId = channelId,
                channelLogin = channelLogin,
                gqlQueryPeriod = gqlQueryPeriod,
                gqlPeriod = gqlPeriod,
                startedAt = started,
                endedAt = ended,
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                helixHeaders = SharedAuthHeaders.helixHeaders(config),
                helixRepository = helixRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getChannelSort(id: String): ChannelSort? {
        return channelSortRepository.getById(id)
    }

    suspend fun saveChannelSort(item: ChannelSort) {
        channelSortRepository.save(item)
    }

    suspend fun deleteChannelSort(item: ChannelSort) {
        channelSortRepository.delete(item)
    }

    fun setFilter(period: String?) {
        filter.value = Filter(period)
    }

    class Filter(
        val period: String?,
    )
}
