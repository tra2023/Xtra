package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchesDao {

    @Query("SELECT * FROM recent_search WHERE type = :type ORDER BY lastSearched DESC")
    fun getAll(type: String): Flow<List<RecentSearch>>

    @Query("SELECT * FROM recent_search WHERE `query` = :query AND type = :type")
    suspend fun getItem(query: String, type: String): RecentSearch?

    @Insert
    suspend fun insert(item: RecentSearch)

    @Delete
    suspend fun delete(item: RecentSearch)

    @Query("DELETE FROM recent_search WHERE `query` NOT IN (SELECT `query` FROM recent_search WHERE type = :type ORDER BY lastSearched DESC LIMIT 20) AND type = :type")
    suspend fun deleteOld(type: String)

    @Transaction
    suspend fun ensureMaxSizeAndInsert(item: RecentSearch) {
        insert(item)
        deleteOld(item.type)
    }

    @Query("DELETE FROM recent_search")
    suspend fun deleteAll()
}