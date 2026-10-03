package com.github.andreyasadchy.xtra.ui.game.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.graphql.type.BroadcastType
import com.github.andreyasadchy.xtra.graphql.type.VideoSort
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideosSort
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GameSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.SharedAuthHeaders
import com.github.andreyasadchy.xtra.repository.datasource.GameVideosDataSource
import com.github.andreyasadchy.xtra.repository.saved.VideoBookmarker
import com.github.andreyasadchy.xtra.settings.XtraSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Game videos tab. Takes [settings] and the navigation arguments as plain values, so it carries no
 * Context and no generated `*FragmentArgs` reference; the Android factory reads the arguments.
 */
class GameVideosViewModel(
    private val settings: XtraSettings,
    private val gameId: String?,
    private val gameSlug: String?,
    private val gameName: String?,
    private val gameSortRepository: GameSortRepository,
    playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val videoBookmarker: VideoBookmarker,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)
    val filtersText = MutableStateFlow<CharSequence?>(null)
    val positions = playerRepository.loadVideoPositions()
    val bookmarks = bookmarksRepository.getAllFlow()

    val sort: String
        get() = filter.value?.sort ?: VideosSort.SORT_VIEWS
    val period: String
        get() = filter.value?.period ?: VideosSort.PERIOD_WEEK
    val type: String
        get() = filter.value?.type ?: VideosSort.VIDEO_TYPE_ALL
    val languages: Array<String>
        get() = filter.value?.languages ?: emptyArray()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        val config = SharedAuthHeaders.loadConfig(settings)
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            GameVideosDataSource(
                gameId = gameId,
                gameSlug = gameSlug,
                gameName = gameName,
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
                    else -> VideoSort.VIEWS
                },
                gqlLanguages = languages.ifEmpty { null }?.toList(),
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
                    else -> "VIEWS"
                },
                helixPeriod = when (period) {
                    VideosSort.PERIOD_DAY -> "day"
                    VideosSort.PERIOD_WEEK -> "week"
                    VideosSort.PERIOD_MONTH -> "month"
                    VideosSort.PERIOD_ALL -> "all"
                    else -> "week"
                },
                helixBroadcastTypes = when (type) {
                    VideosSort.VIDEO_TYPE_ALL -> "all"
                    VideosSort.VIDEO_TYPE_ARCHIVE -> "archive"
                    VideosSort.VIDEO_TYPE_HIGHLIGHT -> "highlight"
                    VideosSort.VIDEO_TYPE_UPLOAD -> "upload"
                    else -> "all"
                },
                helixLanguage = languages.singleOrNull()?.lowercase(),
                helixSort = when (sort) {
                    VideosSort.SORT_TIME -> "time"
                    VideosSort.SORT_VIEWS -> "views"
                    else -> "views"
                },
                gqlHeaders = SharedAuthHeaders.gqlHeaders(config),
                graphQLRepository = graphQLRepository,
                helixHeaders = SharedAuthHeaders.helixHeaders(config),
                helixRepository = helixRepository,
                enableIntegrity = config.enableIntegrity,
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getGameSort(id: String): GameSort? {
        return gameSortRepository.getById(id)
    }

    suspend fun saveGameSort(item: GameSort) {
        gameSortRepository.save(item)
    }

    suspend fun deleteGameSort(item: GameSort) {
        gameSortRepository.delete(item)
    }

    fun setFilter(sort: String?, period: String?, type: String?, languages: Array<String>?) {
        filter.value = Filter(sort, period, type, languages)
    }

    class Filter(
        val sort: String?,
        val period: String?,
        val type: String?,
        val languages: Array<String>?,
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
