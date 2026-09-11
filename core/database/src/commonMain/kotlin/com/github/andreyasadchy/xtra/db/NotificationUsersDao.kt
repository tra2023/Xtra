package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.NotificationUser

@Dao
interface NotificationUsersDao {

    @Query("SELECT * FROM notifications")
    suspend fun getAll(): List<NotificationUser>

    @Query("SELECT * FROM notifications WHERE channelId = :id")
    suspend fun getById(id: String): NotificationUser?

    @Insert
    suspend fun insert(item: NotificationUser)

    @Delete
    suspend fun delete(item: NotificationUser)
}