package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Test

class PicturePerformanceRuntimeTest {
    @Test fun `normal playback respects full request`() {
        val runtime = PicturePerformanceRuntime()
        runtime.setSource(1920, 1080, 24f)
        runtime.recordWindow(0, 240)
        assertEquals(PictureAdaptivePerformancePolicy.Budget.FULL, runtime.plan(1f).budget)
    }

    @Test fun `dropped frames reduce workload`() {
        val runtime = PicturePerformanceRuntime()
        runtime.setSource(1920, 1080, 24f)
        runtime.recordWindow(15, 100)
        assertEquals(PictureAdaptivePerformancePolicy.Load.CRITICAL, runtime.plan(1f).load)
        assertEquals(PictureAdaptivePerformancePolicy.Budget.ECONOMY, runtime.plan(1f).budget)
        runtime.resetWindow()
        assertEquals(PictureAdaptivePerformancePolicy.Load.NORMAL, runtime.plan(1f).load)
    }

    @Test fun `thermal pressure reduces workload independently`() {
        val runtime = PicturePerformanceRuntime()
        runtime.setSource(1280, 720, 30f)
        runtime.setThermalStatus(4)
        assertEquals(PictureAdaptivePerformancePolicy.Budget.ECONOMY, runtime.plan(1f).budget)
    }

    @Test fun `new source resets frame window`() {
        val runtime = PicturePerformanceRuntime()
        runtime.recordWindow(20, 100)
        runtime.setSource(1920, 1080, 24f)
        assertEquals(PictureAdaptivePerformancePolicy.Load.NORMAL, runtime.plan(1f).load)
    }

    @Test fun `invalid telemetry is bounded`() {
        val runtime = PicturePerformanceRuntime()
        runtime.setSource(-1, -1, Float.NaN)
        runtime.recordWindow(200, 10)
        assertEquals(PictureAdaptivePerformancePolicy.Load.CRITICAL, runtime.plan(.9f).load)
    }
}
