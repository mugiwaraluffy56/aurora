package com.aurora.cinema.playback

import com.aurora.cinema.library.db.PlaybackProgressEntity
import com.aurora.cinema.library.db.VideoDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackProgressStore(
    private val videoDao: VideoDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun loadPosition(videoId: Long): Long = withContext(ioDispatcher) {
        videoDao.getProgress(videoId)?.positionMs ?: 0L
    }

    suspend fun savePosition(
        videoId: Long,
        positionMs: Long,
        durationMs: Long,
    ) = withContext(ioDispatcher) {
        val completed = durationMs > 0L && positionMs >= durationMs - COMPLETED_THRESHOLD_MS
        videoDao.insertOrReplaceProgress(
            PlaybackProgressEntity(
                videoId = videoId,
                positionMs = positionMs.coerceAtLeast(0L),
                completed = completed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val COMPLETED_THRESHOLD_MS = 30_000L
    }
}
