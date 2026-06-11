package com.aurora.cinema.media

data class CodecCapabilityResult(
    val decoderName: String,
    val codecFamily: String,
    val status: CodecSupportStatus,
    val warnings: List<String>,
)

data class CodecSummary(
    val codecFamily: String,
    val mimeType: String,
    val decoderName: String,
    val supported: Boolean,
)
