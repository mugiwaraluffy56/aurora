package com.aurora.cinema.media

data class MediaProbeResult(
    val containerMimeType: String,
    val videoMimeType: String,
    val codecFamily: String,
    val profileLevel: String,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Long,
    val bitDepth: Int,
    val hdrFormat: String,
    val decoderName: String,
    val supportStatus: CodecSupportStatus,
    val warnings: List<String>,
) {
    val warningText: String
        get() = warnings.joinToString(separator = "\n")

    companion object {
        fun unknown(mimeType: String = ""): MediaProbeResult {
            return MediaProbeResult(
                containerMimeType = mimeType,
                videoMimeType = mimeType,
                codecFamily = "Unknown",
                profileLevel = "Unknown",
                width = 0,
                height = 0,
                frameRate = 0f,
                bitrate = 0L,
                bitDepth = 0,
                hdrFormat = "Unknown",
                decoderName = "",
                supportStatus = CodecSupportStatus.Unknown,
                warnings = listOf("Codec support has not been probed yet."),
            )
        }
    }
}
