package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegVideoOutputBindingTest {

    @Test
    fun attachStoresOutputTarget() {
        val binding = FfmpegVideoOutputBinding()
        val target = FfmpegVideoOutputTarget(
            widthPx = 1920,
            heightPx = 1080,
        )

        binding.attach(target)

        assertEquals(target, binding.target)
    }

    @Test
    fun replacingTargetUsesLatestTarget() {
        val binding = FfmpegVideoOutputBinding()

        binding.attach(
            FfmpegVideoOutputTarget(
                widthPx = 1920,
                heightPx = 1080,
            ),
        )
        val replacement = FfmpegVideoOutputTarget(
            widthPx = 1280,
            heightPx = 720,
            rotationDegrees = 90,
        )

        binding.attach(replacement)

        assertEquals(replacement, binding.target)
    }

    @Test
    fun detachClearsOutputTargetAndIsSafeWhenRepeated() {
        val binding = FfmpegVideoOutputBinding()
        binding.attach(
            FfmpegVideoOutputTarget(
                widthPx = 1920,
                heightPx = 1080,
            ),
        )

        binding.detach()
        binding.detach()

        assertNull(binding.target)
    }

    @Test
    fun ninetyDegreeRotationSwapsDisplayDimensions() {
        val target = FfmpegVideoOutputTarget(
            widthPx = 1920,
            heightPx = 1080,
            rotationDegrees = 90,
        )

        assertEquals(1080, target.displayWidthPx)
        assertEquals(1920, target.displayHeightPx)
    }

    @Test
    fun zeroDegreeRotationKeepsDisplayDimensions() {
        val target = FfmpegVideoOutputTarget(
            widthPx = 1920,
            heightPx = 1080,
            rotationDegrees = 0,
        )

        assertEquals(1920, target.displayWidthPx)
        assertEquals(1080, target.displayHeightPx)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWidthIsRejected() {
        FfmpegVideoOutputTarget(
            widthPx = 0,
            heightPx = 1080,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHeightIsRejected() {
        FfmpegVideoOutputTarget(
            widthPx = 1920,
            heightPx = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRotationIsRejected() {
        FfmpegVideoOutputTarget(
            widthPx = 1920,
            heightPx = 1080,
            rotationDegrees = 45,
        )
    }
}
