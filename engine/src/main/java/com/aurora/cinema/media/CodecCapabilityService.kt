package com.aurora.cinema.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

class CodecCapabilityService : CodecCapabilityEvaluator {
    fun summarizeDeviceCodecs(): List<CodecSummary> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val supportedFamilies = targetMimeTypes.map { mimeType ->
            val decoder = findDecoder(mimeType)
            CodecSummary(
                codecFamily = codecFamilyForMime(mimeType),
                mimeType = mimeType,
                decoderName = decoder?.name.orEmpty(),
                supported = decoder != null,
            )
        }
        return supportedFamilies
    }

    override fun evaluate(
        videoMimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
        bitDepth: Int,
        hdrFormat: String,
    ): CodecCapabilityResult {
        val mimeType = normalizeMime(videoMimeType)
        if (mimeType.isBlank()) {
            val classification = CodecSupportClassifier.classify(
                decoderName = "",
                videoMimeType = videoMimeType,
                width = width,
                height = height,
                sizeSupported = null,
                bitDepth = bitDepth,
                hdrFormat = hdrFormat,
            )
            return CodecCapabilityResult(
                decoderName = "",
                codecFamily = "Unknown",
                status = classification.status,
                warnings = classification.warnings,
            )
        }

        val decoder = findDecoder(mimeType)
        val sizeSupported = decoder?.let {
            isSizeSupported(it, mimeType, width, height, frameRate)
        }
        val classification = CodecSupportClassifier.classify(
            decoderName = decoder?.name.orEmpty(),
            videoMimeType = mimeType,
            width = width,
            height = height,
            sizeSupported = sizeSupported,
            bitDepth = bitDepth,
            hdrFormat = hdrFormat,
        )
        return CodecCapabilityResult(
            decoderName = decoder?.name.orEmpty(),
            codecFamily = codecFamilyForMime(mimeType),
            status = classification.status,
            warnings = classification.warnings,
        )
    }

    private fun findDecoder(mimeType: String): MediaCodecInfo? {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .firstOrNull { codecInfo ->
                !codecInfo.isEncoder && codecInfo.supportedTypes.any { supportedType ->
                    supportedType.equals(mimeType, ignoreCase = true)
                }
            }
    }

    private fun isSizeSupported(
        codecInfo: MediaCodecInfo,
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
    ): Boolean? {
        if (width <= 0 || height <= 0) return null
        return runCatching {
            val capabilities = codecInfo.getCapabilitiesForType(mimeType)
            val videoCapabilities = capabilities.videoCapabilities ?: return null
            if (frameRate > 0f) {
                videoCapabilities.areSizeAndRateSupported(width, height, frameRate.toDouble())
            } else {
                videoCapabilities.isSizeSupported(width, height)
            }
        }.getOrNull()
    }

    private fun normalizeMime(mimeType: String): String {
        return when {
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_AVC
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_HEVC
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_AV1
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_VP9
            mimeType.startsWith("video/") -> mimeType
            else -> ""
        }
    }

    private fun codecFamilyForMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264 / AVC"
            MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265 / HEVC"
            MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
            MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
            else -> mimeType.ifBlank { "Unknown" }
        }
    }

    private companion object {
        val targetMimeTypes = listOf(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            MediaFormat.MIMETYPE_VIDEO_AV1,
            MediaFormat.MIMETYPE_VIDEO_VP9,
        )
    }
}
