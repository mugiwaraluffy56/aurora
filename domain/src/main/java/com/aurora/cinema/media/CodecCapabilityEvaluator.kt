package com.aurora.cinema.media

interface CodecCapabilityEvaluator {
    fun evaluate(
        videoMimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
        bitDepth: Int,
        hdrFormat: String,
    ): CodecCapabilityResult
}
