package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloMotionEngineTest {
    @Test fun firstSampleAnchorsCursorWithoutJump() {
        val point = HaloMotionEngine().update(0.4f, 0.6f, 16L)
        assertEquals(0.4f, point.x, 0.0001f)
        assertEquals(0.6f, point.y, 0.0001f)
    }

    @Test fun tinyMovementIsNotDiscardedByDeadZone() {
        val engine = HaloMotionEngine()
        engine.update(0.5f, 0.5f, 16L)
        assertTrue(engine.update(0.501f, 0.5f, 16L).x > 0.5f)
    }

    @Test fun decisiveMovementReceivesAcceleration() {
        val engine = HaloMotionEngine()
        engine.update(0.2f, 0.5f, 16L)
        assertTrue(engine.update(0.4f, 0.5f, 16L).x > 0.39f)
    }

    @Test fun cursorAlwaysRemainsInsideNormalisedViewport() {
        val engine = HaloMotionEngine()
        engine.update(0.9f, 0.9f, 16L)
        val point = engine.update(1f, 1f, 4L)
        assertTrue(point.x in 0f..1f)
        assertTrue(point.y in 0f..1f)
    }

    @Test fun resetCanReanchorAfterOrientationOrDisplayChange() {
        val engine = HaloMotionEngine()
        engine.update(0.1f, 0.1f, 16L)
        engine.update(0.8f, 0.8f, 16L)
        engine.reset(HaloVector(0.25f, 0.75f))
        val point = engine.update(0.25f, 0.75f, 16L)
        assertEquals(0.25f, point.x, 0.0001f)
        assertEquals(0.75f, point.y, 0.0001f)
    }

    @Test fun motionIntoEdgeIsSoftened() {
        val normal = HaloMotionEngine(HaloMotionConfig(softEdgeFraction = 0f))
        normal.update(0.96f, 0.5f, 16L)
        val normalPoint = normal.update(0.98f, 0.5f, 16L)

        val softened = HaloMotionEngine()
        softened.update(0.96f, 0.5f, 16L)
        val softenedPoint = softened.update(0.98f, 0.5f, 16L)

        assertTrue(softenedPoint.x < normalPoint.x)
        assertTrue(softenedPoint.x > 0.96f)
    }

    @Test fun motionAwayFromEdgeIsNotSoftened() {
        val normal = HaloMotionEngine(HaloMotionConfig(softEdgeFraction = 0f))
        normal.update(0.96f, 0.5f, 16L)
        val normalPoint = normal.update(0.94f, 0.5f, 16L)

        val softened = HaloMotionEngine()
        softened.update(0.96f, 0.5f, 16L)
        val softenedPoint = softened.update(0.94f, 0.5f, 16L)

        assertEquals(normalPoint.x, softenedPoint.x, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidGainConfigurationIsRejected() {
        HaloMotionConfig(precisionGain = 1.2f, fastGain = 1.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSoftEdgeConfigurationIsRejected() {
        HaloMotionConfig(softEdgeFraction = 0.30f)
    }
}
