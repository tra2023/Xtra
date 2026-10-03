package com.github.andreyasadchy.xtra.ui.following.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.FollowedChannelsSort
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.ChannelSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.FollowedChannelsDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Followed channels tab. Takes [settings] instead of a Context. The query is viewer-scoped, so the
 * GQL headers include the token, exactly as the original `TwitchApiHelper.getGQLHeaders(ctx, true)`.
 */
class FollowedChannelsViewModel(
    private val settings: XtraSettings,
    private val channelSortRepository: ChannelSortRepository,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val sort: String
        get() = filter.value?.sort ?: FollowedChannelsSort.DEFAULT_SORT
    val order: String
        get() = filter.value?.order ?: FollowedChannelsSort.DEFAULT_ORDER

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        val config = SharedAuthHeaders.loadConfig(settings)
        Pager(
            PagingConfig(pageSize = 15, prefetchDistance = 5, initialLoadSize = 15)
        ) {
            FollowedChannelsDataSource(
                userId = settings.getString(C.USER_ID, null),
                sort = when (sort) {
                    FollowedChannelsSort.SORT_FOLLOWED_AT -> "created_at"
                    FollowedChannelsSort.SORT_ALPHABETICALLY -> "login"
                    FollowedChannelsSort.SORT_LAST_BROADCAST -> "last_broadcast"
                    else -> "last_broadcast"
                },
                order = when (order) {
                    FollowedChannelsSort.ORDER_DESC -> "desc"
                    FollowedChannelsSort.ORDER_ASC -> "asc"
                    else -> "desc"
                },
                localChannelFollowsRepository = localChannelFollowsRepository,
                offlineVideosRepository = offlineVideosRepository,
                bookmarksRepository = bookmarksRepository,
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config, includeToken = true),
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

    fun setFilter(sort: String?, order: String?) {
        filter.value = Filter(sort, order)
    }

    class Filter(
        val sort: String?,
        val order: String?,
    )
}
