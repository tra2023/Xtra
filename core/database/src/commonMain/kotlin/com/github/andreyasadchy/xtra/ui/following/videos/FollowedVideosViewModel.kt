package com.github.andreyasadchy.xtra.ui.following.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
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
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.FollowedVideosDataSource
import com.github.andreyasadchy.xtra.repository.saved.VideoBookmarker
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Followed videos tab. Takes [settings] instead of a Context, so the paging setup is identical on
 * Android and desktop; the Android factory supplies the settings bridge.
 *
 * The followed-videos query is viewer-scoped, so the headers include the token.
 */
class FollowedVideosViewModel(
    private val settings: XtraSettings,
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
        val config = SharedAuthHeaders.loadConfig(settings)
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
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config, includeToken = true),
                graphQLRepository = graphQLRepository,
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
}
