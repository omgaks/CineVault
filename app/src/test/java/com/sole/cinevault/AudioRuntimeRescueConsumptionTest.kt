package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeRescueConsumptionTest {

    @Test
    fun matchingVideoCanConsumeRescuePlan() {
        assertTrue(
            shouldConsumeAudioRuntimeRescuePlan(
                rescueVideoPath = "/movies/film.mkv",
                expectedVideoPath = "/movies/film.mkv",
            ),
        )
    }

    @Test
    fun unrelatedVideoCannotConsumeRescuePlan() {
        assertFalse(
            shouldConsumeAudioRuntimeRescuePlan(
                rescueVideoPath = "/movies/film.mkv",
                expectedVideoPath = "/shows/episode.mkv",
            ),
        )
    }

    @Test
    fun missingRescueScopeCannotBeConsumed() {
        assertFalse(
            shouldConsumeAudioRuntimeRescuePlan(
                rescueVideoPath = null,
                expectedVideoPath = "/movies/film.mkv",
            ),
        )
    }

    @Test
    fun missingExpectedVideoCannotConsumeRescuePlan() {
        assertFalse(
            shouldConsumeAudioRuntimeRescuePlan(
                rescueVideoPath = "/movies/film.mkv",
                expectedVideoPath = null,
            ),
        )
    }
}
