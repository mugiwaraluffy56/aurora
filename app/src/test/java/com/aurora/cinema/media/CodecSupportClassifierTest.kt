package com.aurora.cinema.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecSupportClassifierTest {
    @Test
    fun missingDecoderIsUnsupported() {
        val classification = CodecSupportClassifier.classify(
            decoderName = "",
            videoMimeType = "video/hevc",
            width = 3840,
            height = 2160,
            sizeSupported = null,
            bitDepth = 10,
            hdrFormat = "HDR10 / PQ",
        )

        assertEquals(CodecSupportStatus.Unsupported, classification.status)
        assertTrue(classification.warnings.any { it.contains("No hardware or software decoder") })
    }

    @Test
    fun supportedDecoderWithoutWarningsIsSupported() {
        val classification = CodecSupportClassifier.classify(
            decoderName = "decoder.avc",
            videoMimeType = "video/avc",
            width = 1920,
            height = 1080,
            sizeSupported = true,
            bitDepth = 8,
            hdrFormat = "SDR",
        )

        assertEquals(CodecSupportStatus.Supported, classification.status)
        assertTrue(classification.warnings.isEmpty())
    }
}
