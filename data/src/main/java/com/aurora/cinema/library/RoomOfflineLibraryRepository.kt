package com.aurora.cinema.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aurora.cinema.library.db.VideoDao
import com.aurora.cinema.library.db.VideoEntity
import com.aurora.cinema.library.db.VideoWithProgress
import com.aurora.cinema.media.CodecSupportStatus
import com.aurora.cinema.media.MediaProbeResult
import java.io.File
import java.security.MessageDigest
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

    override val videos: Flow<List<VideoItem>> = videoDao.observeVideosWithProgress().map { rows ->
        rows.map { row -> row.toItem() }
    }

    override suspend fun importVideo(uri: Uri, sourceType: String): ImportResult = withContext(ioDispatcher) {
        persistReadPermission(uri)
        val metadata = metadataReader.read(
            uri = uri,
            sourceType = sourceType,
            persistedPermission = hasPersistedPermission(uri),
        )
        val existing = videoDao.getVideoByUri(uri.toString())
        videoDao.insertOrReplace(metadata.toEntity(now = System.currentTimeMillis(), existing = existing))
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
                val existing = videoDao.getVideoByUri(document.uri.toString())
                videoDao.insertOrReplace(metadata.toEntity(now = System.currentTimeMillis(), existing = existing))
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

    override suspend fun renameDisplayTitle(videoId: Long, title: String) = withContext(ioDispatcher) {
        videoDao.updateDisplayTitle(videoId, title.trim())
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

    private fun VideoMetadata.toEntity(now: Long, existing: VideoEntity?): VideoEntity {
        return VideoEntity(
            id = existing?.id ?: 0L,
            uri = uri,
            displayName = displayName,
            displayTitleOverride = existing?.displayTitleOverride.orEmpty(),
            thumbnailPath = existing?.thumbnailPath?.ifBlank { null } ?: createThumbnail(Uri.parse(uri)).orEmpty(),
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            dateAdded = existing?.dateAdded ?: now,
            lastSeenAt = now,
            sourceType = sourceType,
            persistedPermission = persistedPermission,
            lastAccessCheckAt = now,
            accessState = accessState.name,
            probeContainerMimeType = probeResult.containerMimeType,
            probeVideoMimeType = probeResult.videoMimeType,
            codecFamily = probeResult.codecFamily,
            profileLevel = probeResult.profileLevel,
            frameRate = probeResult.frameRate,
            bitrate = probeResult.bitrate,
            bitDepth = probeResult.bitDepth,
            hdrFormat = probeResult.hdrFormat,
            decoderName = probeResult.decoderName,
            codecSupportStatus = probeResult.supportStatus.name,
            codecWarnings = probeResult.warningText,
        )
    }

    private fun VideoWithProgress.toItem(): VideoItem {
        val entity = video
        return VideoItem(
            id = entity.id,
            uri = entity.uri,
            displayName = entity.displayTitleOverride.ifBlank { entity.displayName },
            displayTitleOverride = entity.displayTitleOverride,
            thumbnailPath = entity.thumbnailPath,
            durationMs = entity.durationMs,
            width = entity.width,
            height = entity.height,
            mimeType = entity.mimeType,
            dateAdded = entity.dateAdded,
            lastSeenAt = entity.lastSeenAt,
            sourceType = entity.sourceType,
            persistedPermission = entity.persistedPermission,
            lastAccessCheckAt = entity.lastAccessCheckAt,
            accessState = runCatching { VideoAccessState.valueOf(entity.accessState) }
                .getOrDefault(VideoAccessState.Unknown),
            probeResult = MediaProbeResult(
                containerMimeType = entity.probeContainerMimeType,
                videoMimeType = entity.probeVideoMimeType,
                codecFamily = entity.codecFamily,
                profileLevel = entity.profileLevel,
                width = entity.width,
                height = entity.height,
                frameRate = entity.frameRate,
                bitrate = entity.bitrate,
                bitDepth = entity.bitDepth,
                hdrFormat = entity.hdrFormat,
                decoderName = entity.decoderName,
                supportStatus = runCatching { CodecSupportStatus.valueOf(entity.codecSupportStatus) }
                    .getOrDefault(CodecSupportStatus.Unknown),
                warnings = entity.codecWarnings.lines().filter { it.isNotBlank() },
            ),
            playbackPositionMs = progress?.positionMs ?: 0L,
            playbackCompleted = progress?.completed ?: false,
            playbackUpdatedAt = progress?.updatedAt ?: 0L,
        )
    }

    private fun createThumbnail(uri: Uri): String? {
        return runCatching {
            val outputFile = File(thumbnailDirectory(), "${uri.toString().sha256()}.jpg")
            if (outputFile.exists() && outputFile.length() > 0L) return outputFile.absolutePath

            val retriever = MediaMetadataRetriever()
            val bitmap = retriever.use {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    it.setDataSource(descriptor.fileDescriptor)
                }
                it.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: it.frameAtTime
            } ?: return null

            outputFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            }
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun thumbnailDirectory(): File {
        return File(context.filesDir, "library-thumbnails").apply { mkdirs() }
    }

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
