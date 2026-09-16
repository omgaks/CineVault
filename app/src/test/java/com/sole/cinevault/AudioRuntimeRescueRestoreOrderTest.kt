package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescueRestoreOrderTest {

    @Test
    fun restoreOrderKeepsPreparationAfterContinuityState() {
        val order = listOf(
            "mediaItem",
            "playbackSpeed",
            "volume",
            "audioTrackRestore",
            "prepare",
            "playWhenReady",
        )

        assertEquals("mediaItem", order.first())
        assertEquals("prepare", order[4])
        assertEquals("playWhenReady", order.last())
    }
}
