package com.aurora.cinema.library

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.aurora.cinema.media.MediaProbe

class AndroidVideoMetadataReader(
    private val contentResolver: ContentResolver,
    private val mediaProbe: MediaProbe,
) {
    fun read(
        uri: Uri,
        sourceType: String,
        persistedPermission: Boolean,
        fallbackName: String? = null,
    ): VideoMetadata {
        val mimeType = contentResolver.getType(uri).orEmpty()
        val displayName = queryDisplayName(uri) ?: fallbackName ?: uri.lastPathSegment ?: "Video"
        val accessState = if (canOpen(uri)) VideoAccessState.Available else VideoAccessState.Missing

        val mediaMetadata = readMediaMetadata(uri)
        val finalMimeType = mimeType.ifBlank { guessMimeType(displayName) }
        return VideoMetadata(
            uri = uri.toString(),
            displayName = displayName,
            durationMs = mediaMetadata.durationMs,
            width = mediaMetadata.width,
            height = mediaMetadata.height,
            mimeType = finalMimeType,
            sourceType = sourceType,
            persistedPermission = persistedPermission,
            accessState = accessState,
            probeResult = mediaProbe.probe(uri, finalMimeType),
        )
    }

    fun isVideo(documentFile: DocumentFile): Boolean {
        val mimeType = documentFile.type.orEmpty()
        val name = documentFile.name.orEmpty()
        return mimeType.startsWith("video/") || videoExtensions.any { extension ->
            name.endsWith(extension, ignoreCase = true)
        }
    }

    fun canOpen(uri: Uri): Boolean {
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.fileDescriptor.valid()
            } ?: false
        }.getOrDefault(false)
    }

    private fun readMediaMetadata(uri: Uri): MediaShape {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.use {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    it.setDataSource(descriptor.fileDescriptor)
                }
                MediaShape(
                    durationMs = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L,
                    width = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                        ?: 0,
                    height = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                        ?: 0,
                )
            }
        }.getOrDefault(MediaShape())
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor: Cursor = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null

        return cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else {
                null
            }
        }
    }

    private fun guessMimeType(displayName: String): String {
        return when {
            displayName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            displayName.endsWith(".webm", ignoreCase = true) -> "video/webm"
            displayName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            else -> "video/mp4"
        }
    }

    private data class MediaShape(
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
    )

    private companion object {
        val videoExtensions = setOf(".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi")
    }
}
