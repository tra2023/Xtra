package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.db.BookmarkDeleteInfo
import com.github.andreyasadchy.xtra.db.BookmarkIgnoredUsersDao
import com.github.andreyasadchy.xtra.db.BookmarksDao
import com.github.andreyasadchy.xtra.db.OfflineVideosDao
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.BookmarkIgnoredUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BookmarksRepository(
    private val bookmarksDao: BookmarksDao,
    private val bookmarkIgnoredUsersDao: BookmarkIgnoredUsersDao,
    private val offlineVideosDao: OfflineVideosDao,
) {

    fun getAllFlow() = bookmarksDao.getAllFlow()

    suspend fun getAll() = withContext(Dispatchers.IO) {
        bookmarksDao.getAll()
    }

    suspend fun getByVideoId(id: String) = withContext(Dispatchers.IO) {
        bookmarksDao.getByVideoId(id)
    }

    suspend fun existsByVideoId(id: String) = withContext(Dispatchers.IO) {
        bookmarksDao.existsByVideoId(id)
    }

    suspend fun getDeleteInfoByVideoId(id: String): BookmarkDeleteInfo? = withContext(Dispatchers.IO) {
        bookmarksDao.getDeleteInfoByVideoId(id)
    }

    suspend fun getByUserId(id: String) = withContext(Dispatchers.IO) {
        bookmarksDao.getByUserId(id)
    }

    suspend fun save(item: Bookmark) = withContext(Dispatchers.IO) {
        bookmarksDao.insert(item)
    }

    suspend fun delete(item: Bookmark) = withContext(Dispatchers.IO) {
        // 1. DB work first with cheap COUNTs (no full-entity loads).
        bookmarksDao.delete(item)
        val deleteThumbnail = !item.videoId.isNullOrBlank() && offlineVideosDao.countByVideoId(item.videoId) == 0
        val deleteLogo = !item.userId.isNullOrBlank() &&
                bookmarksDao.countByUserIdExcluding(item.userId, item.id) == 0 &&
                offlineVideosDao.countByUserId(item.userId) == 0
        // 2. File IO after DB, off the critical path.
        if (deleteThumbnail) {
            item.thumbnail?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        }
        if (deleteLogo) {
            item.userLogo?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        }
    }

    /**
     * Toggle-off path for VideoSearchViewModel: 1 narrow row read (id + file
     * paths) + DELETE by videoId + 3 COUNTs. No SELECT * anywhere.
     */
    suspend fun deleteByVideoId(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val info = bookmarksDao.getDeleteInfoByVideoId(videoId) ?: return@withContext false
        bookmarksDao.deleteByVideoId(videoId)
        val deleteThumbnail = !info.videoId.isNullOrBlank() && offlineVideosDao.countByVideoId(info.videoId) == 0
        val deleteLogo = !info.userId.isNullOrBlank() &&
                bookmarksDao.countByUserIdExcluding(info.userId, info.id) == 0 &&
                offlineVideosDao.countByUserId(info.userId) == 0
        if (deleteThumbnail) {
            info.thumbnail?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        }
        if (deleteLogo) {
            info.userLogo?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        }
        true
    }

    suspend fun update(item: Bookmark) = withContext(Dispatchers.IO) {
        bookmarksDao.update(item)
    }

    fun getIgnoredUsersFlow() = bookmarkIgnoredUsersDao.getAllFlow()

    suspend fun getIgnoredUsers() = withContext(Dispatchers.IO) {
        bookmarkIgnoredUsersDao.getAll()
    }

    suspend fun getIgnoredUser(id: String) = withContext(Dispatchers.IO) {
        bookmarkIgnoredUsersDao.getById(id)
    }

    suspend fun saveIgnoredUser(item: BookmarkIgnoredUser) = withContext(Dispatchers.IO) {
        bookmarkIgnoredUsersDao.insert(item)
    }

    suspend fun deleteIgnoredUser(item: BookmarkIgnoredUser) = withContext(Dispatchers.IO) {
        bookmarkIgnoredUsersDao.delete(item)
    }
}
