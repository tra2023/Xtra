package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.LocalGameFollow

@Dao
interface LocalGameFollowsDao {

    @Query("SELECT * FROM local_follows_games")
    suspend fun getAll(): List<LocalGameFollow>

    @Query("SELECT * FROM local_follows_games WHERE gameId = :id")
    suspend fun getById(id: String): LocalGameFollow?

    @Insert
    suspend fun insert(item: LocalGameFollow)

    @Delete
    suspend fun delete(item: LocalGameFollow)

    @Update
    suspend fun update(item: LocalGameFollow)
}
