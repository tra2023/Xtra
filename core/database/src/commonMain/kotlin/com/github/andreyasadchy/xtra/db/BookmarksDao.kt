package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarksDao {

    @Query("SELECT * FROM bookmarks")
    fun getAllFlow(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAll(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE videoId = :id")
    suspend fun getByVideoId(id: String): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE userId = :id")
    suspend fun getByUserId(id: String): List<Bookmark>

    @Insert
    suspend fun insert(item: Bookmark)

    @Delete
    suspend fun delete(item: Bookmark)

    @Update
    suspend fun update(item: Bookmark)
}
