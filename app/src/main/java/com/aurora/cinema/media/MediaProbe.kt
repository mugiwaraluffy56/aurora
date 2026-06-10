package com.aurora.cinema.media

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import android.net.Uri

class MediaProbe(
    private val contentResolver: ContentResolver,
    private val codecCapabilityService: CodecCapabilityService,
) {
    fun probe(uri: Uri, fallbackMimeType: String): MediaProbeResult {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.use {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    it.setDataSource(descriptor.fileDescriptor)
                }

                val containerMimeType = fallbackMimeType.ifBlank {
                    it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
                }
                val track = probeVideoTrack(uri)
                val width = track?.width ?: it.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = track?.height ?: it.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val frameRate = track?.frameRate ?: it.floatMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val bitrate = track?.bitrate ?: it.longMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                val bitDepth = track?.bitDepth ?: inferBitDepth(it)
                val hdrFormat = track?.hdrFormat ?: inferHdrFormat(it)
                val videoMimeType = track?.mimeType.orEmpty()
                val capability = codecCapabilityService.evaluate(
                    videoMimeType = videoMimeType,
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    bitDepth = bitDepth,
                    hdrFormat = hdrFormat,
                )

                MediaProbeResult(
                    containerMimeType = containerMimeType,
                    videoMimeType = videoMimeType,
                    codecFamily = capability.codecFamily,
                    profileLevel = track?.profileLevel ?: "Unknown",
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    bitrate = bitrate,
                    bitDepth = bitDepth,
                    hdrFormat = hdrFormat,
                    decoderName = capability.decoderName,
                    supportStatus = capability.status,
                    warnings = capability.warnings,
                )
            }
        }.getOrElse {
            MediaProbeResult.unknown(fallbackMimeType)
        }
    }

    private fun probeVideoTrack(uri: Uri): VideoTrackProbe? {
        val extractor = MediaExtractor()
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.declaredLength >= 0) {
                    extractor.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                } else {
                    extractor.setDataSource(descriptor.fileDescriptor)
                }
            } ?: return null

            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mimeType.startsWith("video/")) {
                    return VideoTrackProbe(
                        mimeType = mimeType,
                        width = format.optionalInt(MediaFormat.KEY_WIDTH),
                        height = format.optionalInt(MediaFormat.KEY_HEIGHT),
                        frameRate = format.optionalFloat(MediaFormat.KEY_FRAME_RATE),
                        bitrate = format.optionalLong(MediaFormat.KEY_BIT_RATE),
                        bitDepth = format.optionalInt(KEY_BIT_DEPTH).takeIf { it > 0 },
                        hdrFormat = inferHdrFormat(format),
                        profileLevel = profileLevel(format),
                    )
                }
            }
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun inferHdrFormat(format: MediaFormat): String {
        val transfer = format.optionalInt(MediaFormat.KEY_COLOR_TRANSFER)
        val standard = format.optionalInt(MediaFormat.KEY_COLOR_STANDARD)
        return hdrFormat(transfer, standard)
    }

    private fun inferHdrFormat(retriever: MediaMetadataRetriever): String {
        val transfer = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
        val standard = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
        return hdrFormat(transfer, standard)
    }

    private fun hdrFormat(transfer: Int, standard: Int): String {
        return when {
            transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10 / PQ"
            transfer == MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
            standard == MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
            transfer > 0 || standard > 0 -> "SDR"
            else -> "Unknown"
        }
    }

    private fun inferBitDepth(retriever: MediaMetadataRetriever): Int {
        val colorFormat = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
        return colorFormat.takeIf { it > 0 } ?: 8
    }

    private fun profileLevel(format: MediaFormat): String {
        val profile = format.optionalInt(MediaFormat.KEY_PROFILE)
        val level = format.optionalInt(MediaFormat.KEY_LEVEL)
        return when {
            profile > 0 && level > 0 -> "Profile $profile / Level $level"
            profile > 0 -> "Profile $profile"
            level > 0 -> "Level $level"
            else -> "Unknown"
        }
    }

    private fun MediaFormat.optionalInt(key: String): Int {
        return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
    }

    private fun MediaFormat.optionalLong(key: String): Long {
        return if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(0L) else 0L
    }

    private fun MediaFormat.optionalFloat(key: String): Float {
        return if (!containsKey(key)) {
            0f
        } else {
            runCatching { getFloat(key) }
                .recoverCatching { getInteger(key).toFloat() }
                .getOrDefault(0f)
        }
    }

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int {
        return extractMetadata(key)?.toIntOrNull() ?: 0
    }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long {
        return extractMetadata(key)?.toLongOrNull() ?: 0L
    }

    private fun MediaMetadataRetriever.floatMetadata(key: Int): Float {
        return extractMetadata(key)?.toFloatOrNull() ?: 0f
    }
}

private data class VideoTrackProbe(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Long,
    val bitDepth: Int?,
    val hdrFormat: String,
    val profileLevel: String,
)

private const val KEY_BIT_DEPTH = "bit-depth"
