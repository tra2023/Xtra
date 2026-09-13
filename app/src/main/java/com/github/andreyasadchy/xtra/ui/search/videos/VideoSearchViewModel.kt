package com.github.andreyasadchy.xtra.ui.search.videos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.RecentSearchesRepository
import com.github.andreyasadchy.xtra.repository.datasource.SearchVideosDataSource
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
class VideoSearchViewModel(
    applicationContext: Context,
    private val recentSearchesRepository: RecentSearchesRepository,
    playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val xtraHttpClient: XtraHttpClient,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val recentSearches = recentSearchesRepository.getAll(RecentSearch.TYPE_VIDEO)
    val positions = playerRepository.loadVideoPositions()
    val bookmarks = bookmarksRepository.getAllFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = _query.flatMapLatest { query ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            SearchVideosDataSource(
                query = query,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                graphQLRepository = graphQLRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
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
        viewModelScope.launch {
            val item = video.id?.let { bookmarksRepository.getByVideoId(it) }
            if (item != null) {
                bookmarksRepository.delete(item)
            } else {
                val downloadedThumbnail = video.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    video.thumbnail.takeIf { !it.isNullOrBlank() }?.let { url ->
                        File(filesDir, "thumbnails").mkdir()
                        val path = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val downloadedLogo = video.channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    video.channelImage.takeIf { !it.isNullOrBlank() }?.let { url ->
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val userTypes = video.channelId?.let {
                    try {
                        val response = graphQLRepository.loadQueryUsersType(gqlHeaders, listOf(it))
                        response.data!!.users?.firstOrNull()?.let {
                            User(
                                id = it.id,
                                broadcasterType = when {
                                    it.roles?.isPartner == true -> "partner"
                                    it.roles?.isAffiliate == true -> "affiliate"
                                    else -> null
                                },
                                type = when {
                                    it.roles?.isStaff == true -> "staff"
                                    else -> null
                                },
                            )
                        }
                    } catch (e: Exception) {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                            try {
                                helixRepository.getUsers(
                                    headers = helixHeaders,
                                    ids = listOf(it)
                                ).data.firstOrNull()?.let {
                                    User(
                                        id = it.id,
                                        login = it.login,
                                        name = it.displayName,
                                        profileImageURL = it.profileImageURL,
                                        type = it.type,
                                        broadcasterType = it.broadcasterType,
                                        createdAt = it.createdAt,
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }
                }
                bookmarksRepository.save(
                    Bookmark(
                        videoId = video.id,
                        userId = video.channelId,
                        userLogin = video.channelLogin,
                        userName = video.channelName,
                        userType = userTypes?.type,
                        userBroadcasterType = userTypes?.broadcasterType,
                        userLogo = downloadedLogo,
                        gameId = video.gameId,
                        gameSlug = video.gameSlug,
                        gameName = video.gameName,
                        title = video.title,
                        createdAt = video.createdAt,
                        thumbnail = downloadedThumbnail,
                        type = video.type,
                        duration = video.durationSeconds?.toString(),
                        animatedPreviewURL = video.animatedPreviewURL
                    )
                )
            }
        }
    }

    companion object {
        val VideoSearchViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                VideoSearchViewModel(application.applicationContext, xtraModule.recentSearchesRepository, xtraModule.playerRepository, xtraModule.bookmarksRepository, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.xtraHttpClient)
            }
        }
    }
}