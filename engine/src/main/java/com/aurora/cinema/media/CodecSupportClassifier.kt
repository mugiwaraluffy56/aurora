package com.aurora.cinema.media

object CodecSupportClassifier {
    fun classify(
        decoderName: String,
        videoMimeType: String,
        width: Int,
        height: Int,
        sizeSupported: Boolean?,
        bitDepth: Int,
        hdrFormat: String,
    ): CodecClassification {
        val warnings = mutableListOf<String>()
        val normalizedMime = videoMimeType.lowercase()

        if (normalizedMime.isBlank()) {
            warnings += "Video codec could not be identified."
        }
        if (decoderName.isBlank()) {
            warnings += "No hardware or software decoder was found for this codec."
            return CodecClassification(CodecSupportStatus.Unsupported, warnings)
        }
        if (sizeSupported == false) {
            warnings += "Decoder exists, but this resolution is not reported as supported."
        }
        if (bitDepth > 8) {
            warnings += "10-bit or higher video may depend on device-specific decoder support."
        }
        if (hdrFormat != "Unknown" && hdrFormat != "SDR") {
            warnings += "HDR metadata was detected; tone mapping may vary by device."
        }
        if (normalizedMime.contains("matroska") || normalizedMime.contains("octet-stream")) {
            warnings += "Container type may need additional validation during playback."
        }

        return when {
            warnings.isEmpty() -> CodecClassification(CodecSupportStatus.Supported, emptyList())
            sizeSupported == false -> CodecClassification(CodecSupportStatus.Risky, warnings)
            else -> CodecClassification(CodecSupportStatus.Risky, warnings)
        }
    }
}

data class CodecClassification(
    val status: CodecSupportStatus,
    val warnings: List<String>,
)
