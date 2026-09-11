package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.VideoSwap

@Dao
interface VideoSwapDao {

    @Query("SELECT * FROM video_swap")
    suspend fun getAll(): List<VideoSwap>

    @Insert
    suspend fun insertList(items: List<VideoSwap>)

    @Update
    suspend fun updateList(items: List<VideoSwap>)

    @Insert
    suspend fun insert(item: VideoSwap): Long

    @Delete
    suspend fun delete(item: VideoSwap)

    @Update
    suspend fun update(item: VideoSwap)
}
