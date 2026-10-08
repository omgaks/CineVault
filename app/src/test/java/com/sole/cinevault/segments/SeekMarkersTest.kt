package com.sole.cinevault.segments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekMarkersTest {
    private fun seg(type: SegmentType, s: Long, e: Long) = SmartSegment(type, s, e, "test")

    @Test fun noDataMeansNoMarkers() {
        assertTrue(seekMarkersFor(SmartSegmentResult(), 100_000L).isEmpty())
    }

    @Test fun unknownDurationMeansNoMarkers() {
        val r = SmartSegmentResult(listOf(seg(SegmentType.INTRO, 0, 10_000)))
        assertTrue(seekMarkersFor(r, 0L).isEmpty())
    }

    @Test fun introAndCreditsBecomeBands() {
        val r = SmartSegmentResult(listOf(
            seg(SegmentType.CREDITS, 90_000, 100_000),
            seg(SegmentType.INTRO, 10_000, 20_000),
        ))
        val m = seekMarkersFor(r, 100_000L)
        assertEquals(2, m.size)
        assertEquals(SeekMarkerKind.SKIPPABLE, m[0].kind)
        assertEquals(0.10f, m[0].startFraction, 0.001f)
        assertEquals(0.20f, m[0].endFraction, 0.001f)
        assertEquals(SeekMarkerKind.CREDITS, m[1].kind)
    }

    @Test fun sceneIsAPoint() {
        val r = SmartSegmentResult(listOf(seg(SegmentType.POST_CREDITS_SCENE, 95_000, 99_000)))
        val m = seekMarkersFor(r, 100_000L).single()
        assertEquals(SeekMarkerKind.SCENE, m.kind)
        assertEquals(m.startFraction, m.endFraction, 0f)
    }

    @Test fun sceneBeyondDurationIsDropped() {
        val r = SmartSegmentResult(listOf(seg(SegmentType.MID_CREDITS_SCENE, 150_000, 160_000)))
        assertTrue(seekMarkersFor(r, 100_000L).isEmpty())
    }

    @Test fun bandsClampToDuration() {
        val r = SmartSegmentResult(listOf(seg(SegmentType.CREDITS, 90_000, 200_000)))
        assertEquals(1.0f, seekMarkersFor(r, 100_000L).single().endFraction, 0f)
    }
}
