package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.StreamProxy

@Dao
interface StreamProxiesDao {

    @Query("SELECT * FROM stream_proxies")
    suspend fun getAll(): List<StreamProxy>

    @Insert
    suspend fun insertList(items: List<StreamProxy>)

    @Update
    suspend fun updateList(items: List<StreamProxy>)

    @Insert
    suspend fun insert(item: StreamProxy): Long

    @Delete
    suspend fun delete(item: StreamProxy)

    @Update
    suspend fun update(item: StreamProxy)
}
