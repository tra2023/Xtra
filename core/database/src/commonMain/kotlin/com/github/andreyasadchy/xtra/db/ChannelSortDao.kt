package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.ui.ChannelSort

@Dao
interface ChannelSortDao {

    @Query("SELECT * FROM sort_channel WHERE id = :id")
    suspend fun getById(id: String): ChannelSort?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChannelSort)

    @Delete
    suspend fun delete(item: ChannelSort)
}
