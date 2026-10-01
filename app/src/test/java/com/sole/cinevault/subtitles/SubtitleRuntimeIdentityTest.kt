package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubtitleRuntimeIdentityTest {

    @Test
    fun sameVideoAndColour_keepSameIdentity() {
        assertEquals(
            subtitleRuntimeIdentity("/movies/a.mkv", "#00E5FF"),
            subtitleRuntimeIdentity("/movies/a.mkv", "#00E5FF"),
        )
    }

    @Test
    fun changingVideo_invalidatesDualRuntimeIdentity() {
        assertNotEquals(
            subtitleRuntimeIdentity("/movies/a.mkv", "#00E5FF"),
            subtitleRuntimeIdentity("/movies/b.mkv", "#00E5FF"),
        )
    }

    @Test
    fun changingSecondaryColour_invalidatesDualRuntimeIdentity() {
        assertNotEquals(
            subtitleRuntimeIdentity("/movies/a.mkv", "#00E5FF"),
            subtitleRuntimeIdentity("/movies/a.mkv", "#FFB300"),
        )
    }
}
