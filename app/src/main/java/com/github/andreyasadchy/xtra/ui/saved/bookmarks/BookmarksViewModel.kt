package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.BookmarkIgnoredUser
import com.github.andreyasadchy.xtra.model.ui.BookmarksSort
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.ChannelSortRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.XtraHttpClient
import com.github.andreyasadchy.xtra.repository.getBytesOrNull
import com.github.andreyasadchy.xtra.repository.saved.BookmarksRefreshController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
class BookmarksViewModel(
    graphQLRepository: GraphQLRepository,
    helixRepository: HelixRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val channelSortRepository: ChannelSortRepository,
    playerRepository: PlayerRepository,
    xtraHttpClient: XtraHttpClient,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()

    val positions = playerRepository.loadVideoPositions()
    val ignoredUsers = bookmarksRepository.getIgnoredUsersFlow()

    private val refreshController = BookmarksRefreshController(
        scope = viewModelScope,
        graphQLRepository = graphQLRepository,
        helixRepository = helixRepository,
        bookmarksRepository = bookmarksRepository,
        thumbnailPath = { filesDir, id ->
            File(filesDir, "thumbnails").mkdir()
            filesDir + File.separator + "thumbnails" + File.separator + id
        },
        writeThumbnail = { path, url ->
            try {
                xtraHttpClient.getBytesOrNull(url)?.let { bytes -> FileOutputStream(path).use { it.write(bytes) } }
            } catch (e: Exception) {
            }
        },
        onIntegrityFailed = { callback ->
            viewModelScope.launch { integrity.emit(callback) }
        },
    )

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val sort: String
        get() = filter.value?.sort ?: BookmarksSort.DEFAULT_SORT
    val order: String
        get() = filter.value?.order ?: BookmarksSort.DEFAULT_ORDER

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        bookmarksRepository.getAllFlow()
    }

    fun delete(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarksRepository.delete(bookmark)
        }
    }

    fun vodIgnoreUser(userId: String) {
        viewModelScope.launch {
            if (bookmarksRepository.getIgnoredUser(userId) != null) {
                bookmarksRepository.deleteIgnoredUser(BookmarkIgnoredUser(userId))
            } else {
                bookmarksRepository.saveIgnoredUser(BookmarkIgnoredUser(userId))
            }
        }
    }

    fun updateUsers(gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        refreshController.updateUsers(gqlHeaders, helixHeaders, enableIntegrity)
    }

    fun updateVideo(filesDir: String, videoId: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        refreshController.updateVideo(filesDir, videoId, gqlHeaders, helixHeaders, enableIntegrity)
    }

    fun updateVideos(filesDir: String, helixHeaders: Map<String, String>) {
        refreshController.updateVideos(filesDir, helixHeaders)
    }

    suspend fun getChannelSort(id: String): ChannelSort? {
        return channelSortRepository.getById(id)
    }

    suspend fun saveChannelSort(item: ChannelSort) {
        channelSortRepository.save(item)
    }

    fun setFilter(sort: String?, order: String?) {
        filter.value = Filter(sort, order)
    }

    class Filter(
        val sort: String?,
        val order: String?,
    )

    companion object {
        val BookmarksViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                BookmarksViewModel(xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.bookmarksRepository, xtraModule.channelSortRepository, xtraModule.playerRepository, xtraModule.xtraHttpClient)
            }
        }
    }
}