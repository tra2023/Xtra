package com.github.andreyasadchy.xtra.ui.following.videos

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
import com.github.andreyasadchy.xtra.graphql.type.BroadcastType
import com.github.andreyasadchy.xtra.graphql.type.VideoSort
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideosSort
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.ChannelSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.saved.VideoBookmarker
import com.github.andreyasadchy.xtra.repository.datasource.FollowedVideosDataSource
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class FollowedVideosViewModel(
    private val applicationContext: Context,
    private val channelSortRepository: ChannelSortRepository,
    playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val videoBookmarker: VideoBookmarker,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)
    val positions = playerRepository.loadVideoPositions()
    val bookmarks = bookmarksRepository.getAllFlow()

    val sort: String
        get() = filter.value?.sort ?: VideosSort.SORT_TIME
    val period: String
        get() = filter.value?.period ?: VideosSort.PERIOD_ALL
    val type: String
        get() = filter.value?.type ?: VideosSort.VIDEO_TYPE_ALL

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            FollowedVideosDataSource(
                gqlQueryType = when (type) {
                    VideosSort.VIDEO_TYPE_ALL -> null
                    VideosSort.VIDEO_TYPE_ARCHIVE -> BroadcastType.ARCHIVE
                    VideosSort.VIDEO_TYPE_HIGHLIGHT -> BroadcastType.HIGHLIGHT
                    VideosSort.VIDEO_TYPE_UPLOAD -> BroadcastType.UPLOAD
                    else -> null
                },
                gqlQuerySort = when (sort) {
                    VideosSort.SORT_TIME -> VideoSort.TIME
                    VideosSort.SORT_VIEWS -> VideoSort.VIEWS
                    else -> VideoSort.TIME
                },
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
                graphQLRepository = graphQLRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getChannelSort(id: String): ChannelSort? {
        return channelSortRepository.getById(id)
    }

    suspend fun saveChannelSort(item: ChannelSort) {
        channelSortRepository.save(item)
    }

    fun setFilter(sort: String?, type: String?) {
        filter.value = Filter(sort, null, type)
    }

    class Filter(
        val sort: String?,
        val period: String?,
        val type: String?,
    )

    fun saveBookmark(filesDir: String, video: Video, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        videoBookmarker.toggle(
            filesDir = filesDir,
            video = video,
            gqlHeaders = gqlHeaders,
            helixHeaders = helixHeaders,
        )
    }

    companion object {
        val FollowedVideosViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                FollowedVideosViewModel(application.applicationContext, xtraModule.channelSortRepository, xtraModule.playerRepository, xtraModule.bookmarksRepository, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.videoBookmarker)
            }
        }
    }
}