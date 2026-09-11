package com.github.andreyasadchy.xtra.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.ui.SavedFilter

@Dao
interface SavedFiltersDao {

    @Query("SELECT * FROM filters")
    fun getAll(): PagingSource<Int, SavedFilter>

    @Insert
    suspend fun insert(item: SavedFilter)

    @Delete
    suspend fun delete(item: SavedFilter)
}