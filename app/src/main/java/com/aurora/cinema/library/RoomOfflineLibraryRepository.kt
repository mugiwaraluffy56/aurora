package com.aurora.cinema.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aurora.cinema.library.db.VideoDao
import com.aurora.cinema.library.db.VideoEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomOfflineLibraryRepository(
    private val context: Context,
    private val videoDao: VideoDao,
    private val metadataReader: AndroidVideoMetadataReader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OfflineLibraryRepository {
    private val contentResolver = context.contentResolver

    override val videos: Flow<List<VideoItem>> = videoDao.observeVideos().map { entities ->
        entities.map { entity -> entity.toItem() }
    }

    override suspend fun importVideo(uri: Uri, sourceType: String): ImportResult = withContext(ioDispatcher) {
        persistReadPermission(uri)
        val metadata = metadataReader.read(
            uri = uri,
            sourceType = sourceType,
            persistedPermission = hasPersistedPermission(uri),
        )
        videoDao.insertOrReplace(metadata.toEntity(now = System.currentTimeMillis()))
        ImportResult(importedCount = 1, skippedCount = 0)
    }

    override suspend fun importFolder(uri: Uri): ImportResult = withContext(ioDispatcher) {
        persistReadPermission(uri)
        val tree = DocumentFile.fromTreeUri(context, uri)
        if (tree == null || !tree.canRead()) {
            return@withContext ImportResult(importedCount = 0, skippedCount = 1)
        }

        var imported = 0
        var skipped = 0
        tree.listFiles().forEach { document ->
            if (document.isFile && metadataReader.isVideo(document)) {
                val metadata = metadataReader.read(
                    uri = document.uri,
                    sourceType = OfflineLibraryRepository.SourceTypes.Folder,
                    persistedPermission = hasPersistedTreePermission(uri),
                    fallbackName = document.name,
                )
                videoDao.insertOrReplace(metadata.toEntity(now = System.currentTimeMillis()))
                imported += 1
            } else {
                skipped += 1
            }
        }

        ImportResult(importedCount = imported, skippedCount = skipped)
    }

    override suspend fun refreshAccessChecks() = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        videoDao.getVideos().forEach { entity ->
            val uri = Uri.parse(entity.uri)
            val accessState = if (metadataReader.canOpen(uri)) {
                VideoAccessState.Available
            } else {
                VideoAccessState.Missing
            }
            videoDao.updateAccessState(
                id = entity.id,
                accessState = accessState.name,
                persistedPermission = hasAnyPersistedPermission(uri),
                lastAccessCheckAt = now,
            )
        }
    }

    override suspend fun deleteLibraryEntry(videoId: Long) {
        videoDao.deleteById(videoId)
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun hasPersistedPermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
    }

    private fun hasPersistedTreePermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
    }

    private fun hasAnyPersistedPermission(uri: Uri): Boolean {
        return hasPersistedPermission(uri) || contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && uri.toString().startsWith(permission.uri.toString())
        }
    }

    private fun VideoMetadata.toEntity(now: Long): VideoEntity {
        return VideoEntity(
            uri = uri,
            displayName = displayName,
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            dateAdded = now,
            lastSeenAt = now,
            sourceType = sourceType,
            persistedPermission = persistedPermission,
            lastAccessCheckAt = now,
            accessState = accessState.name,
        )
    }

    private fun VideoEntity.toItem(): VideoItem {
        return VideoItem(
            id = id,
            uri = uri,
            displayName = displayName,
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            dateAdded = dateAdded,
            lastSeenAt = lastSeenAt,
            sourceType = sourceType,
            persistedPermission = persistedPermission,
            lastAccessCheckAt = lastAccessCheckAt,
            accessState = runCatching { VideoAccessState.valueOf(accessState) }
                .getOrDefault(VideoAccessState.Unknown),
        )
    }
}
