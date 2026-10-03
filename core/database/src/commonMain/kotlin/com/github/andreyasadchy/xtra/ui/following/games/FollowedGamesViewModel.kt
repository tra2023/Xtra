package com.github.andreyasadchy.xtra.ui.following.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalGameFollowsRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.FollowedGamesDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings

/**
 * Followed games tab. Takes [settings] instead of a Context. The query is viewer-scoped, so the GQL
 * headers include the token, exactly as the original `TwitchApiHelper.getGQLHeaders(ctx, true)`.
 */
class FollowedGamesViewModel(
    private val settings: XtraSettings,
    private val localGameFollowsRepository: LocalGameFollowsRepository,
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
    ) {
        val config = SharedAuthHeaders.loadConfig(settings)
        FollowedGamesDataSource(
            localGameFollowsRepository = localGameFollowsRepository,
            gqlHeaders = SharedAuthHeaders.gqlHeaders(config, includeToken = true),
            graphQLRepository = graphQLRepository,
            enableIntegrity = config.enableIntegrity,
        )
    }.flow.cachedIn(viewModelScope)
}
