package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAdaptivePerformancePolicyTest {
    private fun input(intensity: Float = 1f) = PictureAdaptivePerformancePolicy.Input(
        sourceWidth = 1920, sourceHeight = 1080, frameRate = 24f, intensity = intensity,
    )

    @Test fun `normal load respects selected quality`() {
        val p = PictureAdaptivePerformancePolicy
        assertEquals(p.Budget.ECONOMY, p.plan(input(.6f)).budget)
        assertEquals(p.Budget.BALANCED, p.plan(input(.9f)).budget)
        assertEquals(p.Budget.FULL, p.plan(input(1f)).budget)
    }

    @Test fun `elevated load limits max without forcing eco`() {
        val p = PictureAdaptivePerformancePolicy
        val result = p.plan(input().copy(thermalStatus = 3))
        assertEquals(p.Load.ELEVATED, result.load)
        assertEquals(p.Budget.BALANCED, result.budget)
    }

    @Test fun `critical load protects playback`() {
        val p = PictureAdaptivePerformancePolicy
        val result = p.plan(input().copy(observedFrames = 100, droppedFrames = 15))
        assertEquals(p.Load.CRITICAL, result.load)
        assertEquals(p.Budget.ECONOMY, result.budget)
    }

    @Test fun `invalid telemetry is safe and output is bounded`() {
        val p = PictureAdaptivePerformancePolicy
        val result = p.plan(p.Input(-1, 0, Float.NaN, Float.NaN, -1, 0, -1))
        assertEquals(p.Load.NORMAL, result.load)
        assertTrue(result.repairScale in 0f..1f)
        assertTrue(result.chromaScale in 0f..1f)
        assertTrue(result.detailScale in 0f..1f)
    }

    @Test fun `high pixel throughput raises workload tier`() {
        val p = PictureAdaptivePerformancePolicy
        val result = p.plan(p.Input(3840, 2160, 60f, 1f))
        assertEquals(p.Load.ELEVATED, result.load)
    }
}
