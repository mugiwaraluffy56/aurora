package com.aurora.cinema.library.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Transaction
    @Query("SELECT * FROM videos ORDER BY lastSeenAt DESC")
    fun observeVideosWithProgress(): Flow<List<VideoWithProgress>>

    @Query("SELECT * FROM videos")
    suspend fun getVideos(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getVideoByUri(uri: String): VideoEntity?

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

    @Query("UPDATE videos SET displayTitleOverride = :title WHERE id = :id")
    suspend fun updateDisplayTitle(id: Long, title: String)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteById(videoId: Long)

    @Query("SELECT * FROM playback_progress WHERE videoId = :videoId")
    suspend fun getProgress(videoId: Long): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceProgress(progress: PlaybackProgressEntity)
}

data class VideoWithProgress(
    @Embedded val video: VideoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "videoId",
    )
    val progress: PlaybackProgressEntity?,
)
