package com.github.andreyasadchy.xtra.ui.search.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.RecentSearchesRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.saved.VideoBookmarker
import com.github.andreyasadchy.xtra.repository.datasource.SearchVideosDataSource
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Video search tab. Takes [settings] instead of a `Context`; [saveBookmark] receives the file
 * directory from the host (the only platform-specific input it needs, and it is used as a plain
 * path, so the bookmark thumbnails land in the same place as before).
 */
class VideoSearchViewModel(
    private val settings: XtraSettings,
    private val recentSearchesRepository: RecentSearchesRepository,
    playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val videoBookmarker: VideoBookmarker,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val recentSearches = recentSearchesRepository.getAll(RecentSearch.TYPE_VIDEO)
    val positions = playerRepository.loadVideoPositions()
    val bookmarks = bookmarksRepository.getAllFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = _query.flatMapLatest { query ->
        val config = SharedAuthHeaders.loadConfig(settings)
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            SearchVideosDataSource(
                query = query,
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }.cachedIn(viewModelScope)

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
        }
    }

    fun saveRecentSearch(query: String) {
        if (query.isNotBlank()) {
            viewModelScope.launch {
                recentSearchesRepository.getItem(query, RecentSearch.TYPE_VIDEO)?.let {
                    recentSearchesRepository.delete(it)
                }
                recentSearchesRepository.save(RecentSearch(query, RecentSearch.TYPE_VIDEO, System.currentTimeMillis()))
            }
        }
    }

    fun deleteRecentSearch(item: RecentSearch) {
        viewModelScope.launch {
            recentSearchesRepository.delete(item)
        }
    }

    fun saveBookmark(filesDir: String, video: Video, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        videoBookmarker.toggle(
            filesDir = filesDir,
            video = video,
            gqlHeaders = gqlHeaders,
            helixHeaders = helixHeaders,
        )
    }
}