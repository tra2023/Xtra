package com.github.andreyasadchy.xtra.ui.following.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.FollowedStreamsDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Followed streams tab. Takes [settings] instead of a Context. The query is viewer-scoped, so the
 * GQL headers include the token, exactly as the original `TwitchApiHelper.getGQLHeaders(ctx, true)`.
 */
class FollowedStreamsViewModel(
    private val settings: XtraSettings,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    val flow = Pager(
        if (settings.getString(C.COMPACT_STREAMS, "disabled") != "disabled") {
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        } else {
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        }
    ) {
        val config = SharedAuthHeaders.loadConfig(settings)
        FollowedStreamsDataSource(
            userId = settings.getString(C.USER_ID, null),
            localChannelFollowsRepository = localChannelFollowsRepository,
            gqlHeaders = SharedAuthHeaders.gqlHeaders(config, includeToken = true),
            graphQLRepository = graphQLRepository,
            helixHeaders = SharedAuthHeaders.helixHeaders(config),
            helixRepository = helixRepository,
            enableIntegrity = config.enableIntegrity,
        )
    }.flow.cachedIn(viewModelScope)
}
