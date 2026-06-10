package com.aurora.cinema.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY lastSeenAt DESC")
    fun observeVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos")
    suspend fun getVideos(): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(video: VideoEntity)

    @Query(
        """
        UPDATE videos
        SET accessState = :accessState,
            persistedPermission = :persistedPermission,
            lastAccessCheckAt = :lastAccessCheckAt
        WHERE id = :id
        """,
    )
    suspend fun updateAccessState(
        id: Long,
        accessState: String,
        persistedPermission: Boolean,
        lastAccessCheckAt: Long,
    )

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteById(videoId: Long)
}
