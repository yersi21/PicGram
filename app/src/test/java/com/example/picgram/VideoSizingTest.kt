package com.example.picgram

import org.junit.Assert.*
import org.junit.Test

class VideoSizingTest {
    @Test fun landscapeVideoUsesAvailableWidthWithoutStretching() {
        val ratio = videoAspectRatio(1920, 1080)
        assertEquals(16f / 9f, ratio, 0.001f)
        assertEquals(202.5f, videoViewportHeight(360f, 500f, ratio), 0.001f)
    }

    @Test fun portraitVideoFitsScreenHeightRatherThanOverflowing() {
        val ratio = videoAspectRatio(1080, 1920)
        assertEquals(9f / 16f, ratio, 0.001f)
        assertEquals(500f, videoViewportHeight(360f, 500f, ratio), 0.001f)
    }

    @Test fun rotatedMetadataAndNonSquarePixelsProduceTheCorrectRatio() {
        assertEquals(9f / 16f, videoAspectRatio(1920, 1080, rotation = 90), 0.001f)
        assertEquals(9f / 16f, videoAspectRatio(1920, 1080, rotation = -90), 0.001f)
        assertEquals(16f / 9f, videoAspectRatio(720, 576, pixelRatio = 64f / 45f), 0.001f)
    }

    @Test fun landscapePhoneAndSplitScreenKeepTheVideoInsideTheViewport() {
        assertEquals(240f, videoViewportHeight(720f, 240f, 16f / 9f), 0.001f)
        assertEquals(112.5f, videoViewportHeight(200f, 400f, 16f / 9f), 0.001f)
    }

    @Test fun badMetadataFallsBackToASafeFiniteRatio() {
        assertEquals(16f / 9f, videoAspectRatio(0, 0), 0.001f)
        assertEquals(16f / 9f, videoAspectRatio(10, 10, Float.NaN), 0.001f)
        assertEquals(202.5f, videoViewportHeight(360f, 500f, Float.NaN), 0.001f)
        assertEquals(0f, videoViewportHeight(Float.POSITIVE_INFINITY, 500f, 1f), 0f)
    }
}
