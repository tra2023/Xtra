package com.github.andreyasadchy.xtra.ui.channel.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.ChannelSuggestionsDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings

/**
 * Channel suggestions tab. Same shape as the other channel tabs: [settings] plus repositories in,
 * the navigation argument as a plain string so the host reads it from its own `SavedStateHandle`.
 */
class ChannelSuggestionsViewModel(
    private val settings: XtraSettings,
    private val channelLogin: String?,
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
    ) {
        val config = SharedAuthHeaders.loadConfig(settings)
        ChannelSuggestionsDataSource(
            channelLogin = channelLogin,
            gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
            graphQLRepository = graphQLRepository,
            enableIntegrity = config.enableIntegrity,
        )
    }.flow.cachedIn(viewModelScope)
}
