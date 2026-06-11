package com.aurora.cinema.library

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface OfflineLibraryRepository {
    val videos: Flow<List<VideoItem>>

    suspend fun importVideo(uri: Uri, sourceType: String = SourceTypes.File): ImportResult

    suspend fun importFolder(uri: Uri): ImportResult

    suspend fun refreshAccessChecks()

    suspend fun renameDisplayTitle(videoId: Long, title: String)

    suspend fun deleteLibraryEntry(videoId: Long)

    object SourceTypes {
        const val File = "saf_file"
        const val Folder = "saf_folder"
    }
}

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
)
