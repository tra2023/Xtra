package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.ui.BookmarkIgnoredUser
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkIgnoredUsersDao {

    @Query("SELECT * FROM vod_bookmark_ignored_users")
    fun getAllFlow(): Flow<List<BookmarkIgnoredUser>>

    @Query("SELECT * FROM vod_bookmark_ignored_users")
    suspend fun getAll(): List<BookmarkIgnoredUser>

    @Query("SELECT * FROM vod_bookmark_ignored_users WHERE user_id = :id")
    suspend fun getById(id: String): BookmarkIgnoredUser?

    @Insert
    suspend fun insert(item: BookmarkIgnoredUser)

    @Delete
    suspend fun delete(item: BookmarkIgnoredUser)
}
