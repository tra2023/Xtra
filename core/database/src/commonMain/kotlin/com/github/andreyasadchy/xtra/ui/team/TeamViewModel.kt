package com.github.andreyasadchy.xtra.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.model.ui.Team
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.TeamMembersDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Team screen: the member paging source plus the team info lookup. The navigation argument arrives
 * as a plain string, so the host reads it from its own `SavedStateHandle` and this stays free of
 * generated navigation classes.
 */
class TeamViewModel(
    private val settings: XtraSettings,
    private val teamName: String?,
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()

    val team = MutableStateFlow<Team?>(null)

    private var isLoading = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
    ) {
        val config = SharedAuthHeaders.loadConfig(settings)
        TeamMembersDataSource(
            teamName = teamName,
            gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
            graphQLRepository = graphQLRepository,
            enableIntegrity = config.enableIntegrity,
        )
    }.flow.cachedIn(viewModelScope)

    fun loadTeamInfo(teamName: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (teamName != null && team.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                val response = try {
                    val response = graphQLRepository.loadQueryTeam(gqlHeaders, teamName)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            isLoading = false
                            return@launch
                        }
                    }
                    response.data!!.team?.let { team ->
                        Team(
                            displayName = team.displayName,
                            description = team.description,
                            logoUrl = team.logoURL,
                            bannerUrl = team.bannerURL,
                            memberCount = team.members?.totalCount,
                            ownerLogin = team.owner?.login,
                            ownerName = team.owner?.displayName,
                        )
                    }
                } catch (e: Exception) {
                    null
                }
                team.value = response
                isLoading = false
            }
        }
    }
}
