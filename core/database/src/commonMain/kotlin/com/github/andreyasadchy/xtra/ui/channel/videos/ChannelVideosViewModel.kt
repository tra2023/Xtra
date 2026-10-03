package com.github.andreyasadchy.xtra.ui.channel.videos

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
import com.github.andreyasadchy.xtra.repository.datasource.ChannelVideosDataSource
import com.github.andreyasadchy.xtra.repository.saved.VideoBookmarker
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Channel videos tab. Takes [settings] and the navigation arguments as plain values, so it carries no
 * Context and no generated `*FragmentArgs` reference; the Android factory reads the arguments.
 */
class ChannelVideosViewModel(
    private val settings: XtraSettings,
    private val channelId: String?,
    private val channelLogin: String?,
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
            ChannelVideosDataSource(
                channelId = channelId,
                channelLogin = channelLogin,
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
                gqlType = when (type) {
                    VideosSort.VIDEO_TYPE_ALL -> null
                    VideosSort.VIDEO_TYPE_ARCHIVE -> "ARCHIVE"
                    VideosSort.VIDEO_TYPE_HIGHLIGHT -> "HIGHLIGHT"
                    VideosSort.VIDEO_TYPE_UPLOAD -> "UPLOAD"
                    else -> null
                },
                gqlSort = when (sort) {
                    VideosSort.SORT_TIME -> "TIME"
                    VideosSort.SORT_VIEWS -> "VIEWS"
                    else -> "TIME"
                },
                helixPeriod = when (period) {
                    VideosSort.PERIOD_DAY -> "day"
                    VideosSort.PERIOD_WEEK -> "week"
                    VideosSort.PERIOD_MONTH -> "month"
                    VideosSort.PERIOD_ALL -> "all"
                    else -> "all"
                },
                helixBroadcastTypes = when (type) {
                    VideosSort.VIDEO_TYPE_ALL -> "all"
                    VideosSort.VIDEO_TYPE_ARCHIVE -> "archive"
                    VideosSort.VIDEO_TYPE_HIGHLIGHT -> "highlight"
                    VideosSort.VIDEO_TYPE_UPLOAD -> "upload"
                    else -> "all"
                },
                helixSort = when (sort) {
                    VideosSort.SORT_TIME -> "time"
                    VideosSort.SORT_VIEWS -> "views"
                    else -> "time"
                },
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
