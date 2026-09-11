package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.ShownNotification

@Dao
interface ShownNotificationsDao {

    @Query("SELECT * FROM shown_notifications")
    suspend fun getAll(): List<ShownNotification>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(items: List<ShownNotification>)

    @Delete
    suspend fun deleteList(items: List<ShownNotification>)
}