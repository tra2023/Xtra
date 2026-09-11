package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.ui.TranslatedChannel

@Dao
interface TranslatedChannelsDao {

    @Query("SELECT * FROM translate_all_messages WHERE channelId = :id")
    suspend fun getById(id: String): TranslatedChannel?

    @Insert
    suspend fun insert(item: TranslatedChannel)

    @Delete
    suspend fun delete(item: TranslatedChannel)
}