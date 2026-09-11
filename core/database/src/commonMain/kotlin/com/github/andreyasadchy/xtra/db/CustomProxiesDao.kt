package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.CustomProxy

@Dao
interface CustomProxiesDao {

    @Query("SELECT * FROM custom_proxies")
    suspend fun getAll(): List<CustomProxy>

    @Insert
    suspend fun insertList(items: List<CustomProxy>)

    @Update
    suspend fun updateList(items: List<CustomProxy>)

    @Insert
    suspend fun insert(item: CustomProxy): Long

    @Delete
    suspend fun delete(item: CustomProxy)

    @Update
    suspend fun update(item: CustomProxy)
}
