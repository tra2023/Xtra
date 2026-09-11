package com.github.andreyasadchy.xtra.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo

@Dao
interface OfflineVideosDao {

    @Query("SELECT * FROM videos ORDER BY id DESC")
    fun getAll(): PagingSource<Int, OfflineVideo>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: Int): OfflineVideo?

    @Query("SELECT * FROM videos WHERE url = :url")
    suspend fun getByUrl(url: String): OfflineVideo?

    @Query("SELECT * FROM videos WHERE status = ${OfflineVideo.STATUS_DOWNLOADING} OR status = ${OfflineVideo.STATUS_WAITING_FOR_STREAM}")
    suspend fun getActiveDownloads(): List<OfflineVideo>

    @Query("SELECT * FROM videos WHERE status = ${OfflineVideo.STATUS_WAITING_FOR_WIFI}")
    suspend fun getWaitingDownloads(): List<OfflineVideo>

    @Query("SELECT * FROM videos WHERE videoId = :id")
    suspend fun getByVideoId(id: String): List<OfflineVideo>

    @Query("SELECT * FROM videos WHERE channel_id = :id")
    suspend fun getByUserId(id: String): List<OfflineVideo>

    @Query("SELECT * FROM videos WHERE lower(url) LIKE '%.m3u8'")
    suspend fun getPlaylists(): List<OfflineVideo>

    @Insert
    suspend fun insert(video: OfflineVideo): Long

    @Delete
    suspend fun delete(video: OfflineVideo)

    @Update
    suspend fun update(video: OfflineVideo)

    @Query("UPDATE videos SET last_watch_position = :position WHERE id = :id")
    suspend fun updatePosition(id: Int, position: Long)

    @Query("UPDATE videos SET last_watch_position = null")
    suspend fun deletePositions()
}
