package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Test

class StereoVideoUvMapperTest {
    @Test
    fun monoUsesTheFullTextureForBothEyes() {
        assertEquals(UvRect(0f, 0f, 1f, 1f), StereoVideoUvMapper.rect(StereoVideoLayout.Mono, rightEye = false, swapEyes = false))
        assertEquals(UvRect(0f, 0f, 1f, 1f), StereoVideoUvMapper.rect(StereoVideoLayout.Mono, rightEye = true, swapEyes = false))
    }

    @Test
    fun sideBySideSplitsHorizontally() {
        assertEquals(UvRect(0f, 0f, 0.5f, 1f), StereoVideoUvMapper.rect(StereoVideoLayout.SideBySide, rightEye = false, swapEyes = false))
        assertEquals(UvRect(0.5f, 0f, 0.5f, 1f), StereoVideoUvMapper.rect(StereoVideoLayout.SideBySide, rightEye = true, swapEyes = false))
    }

    @Test
    fun overUnderSplitsVertically() {
        assertEquals(UvRect(0f, 0f, 1f, 0.5f), StereoVideoUvMapper.rect(StereoVideoLayout.OverUnder, rightEye = false, swapEyes = false))
        assertEquals(UvRect(0f, 0.5f, 1f, 0.5f), StereoVideoUvMapper.rect(StereoVideoLayout.OverUnder, rightEye = true, swapEyes = false))
    }

    @Test
    fun swapEyesReversesStereoHalves() {
        assertEquals(UvRect(0.5f, 0f, 0.5f, 1f), StereoVideoUvMapper.rect(StereoVideoLayout.SideBySide, rightEye = false, swapEyes = true))
        assertEquals(UvRect(0f, 0f, 0.5f, 1f), StereoVideoUvMapper.rect(StereoVideoLayout.SideBySide, rightEye = true, swapEyes = true))
    }
}
