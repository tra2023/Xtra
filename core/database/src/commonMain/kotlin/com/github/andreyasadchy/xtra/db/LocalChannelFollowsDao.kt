package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.LocalChannelFollow

@Dao
interface LocalChannelFollowsDao {

    @Query("SELECT * FROM local_follows")
    suspend fun getAll(): List<LocalChannelFollow>

    @Query("SELECT * FROM local_follows WHERE userId = :id")
    suspend fun getById(id: String): LocalChannelFollow?

    @Insert
    suspend fun insert(item: LocalChannelFollow)

    @Delete
    suspend fun delete(item: LocalChannelFollow)

    @Update
    suspend fun update(item: LocalChannelFollow)
}
