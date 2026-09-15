package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureHistoryPresentationTest {

    @Test
    fun emptyHistoryHasNoPresentation() {
        assertNull(presentPlaybackFailureHistory(emptyList()))
    }

    @Test
    fun presentationShowsOnlyLatestThreeEventsByDefault() {
        val history = listOf(
            entry(PlaybackFailureStreamKind.VIDEO, 4000, PlaybackFailureHistoryOutcome.RETRYING),
            entry(PlaybackFailureStreamKind.VIDEO, 4001, PlaybackFailureHistoryOutcome.RETRYING),
            entry(PlaybackFailureStreamKind.VIDEO, 4002, PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE),
            entry(PlaybackFailureStreamKind.AUDIO, 4003, PlaybackFailureHistoryOutcome.TERMINAL),
        )

        val result = presentPlaybackFailureHistory(history)!!

        assertEquals(3, result.latestEvents.size)
        assertEquals("Video · retry · error 4001", result.latestEvents[0])
        assertEquals("Video · software rescue · error 4002", result.latestEvents[1])
        assertEquals("Audio · failed · error 4003", result.latestEvents[2])
        assertTrue(result.hasTerminalFailure)
    }

    @Test
    fun summaryPreservesCountsAcrossFullHistoryNotOnlyVisibleEvents() {
        val history = listOf(
            entry(PlaybackFailureStreamKind.VIDEO, 4000, PlaybackFailureHistoryOutcome.RETRYING),
            entry(PlaybackFailureStreamKind.VIDEO, 4001, PlaybackFailureHistoryOutcome.RETRYING),
            entry(PlaybackFailureStreamKind.VIDEO, 4002, PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE),
            entry(PlaybackFailureStreamKind.AUDIO, 4003, PlaybackFailureHistoryOutcome.TERMINAL),
        )

        val result = presentPlaybackFailureHistory(history, maxVisibleEvents = 2)!!

        assertEquals("1 terminal · 1 rescue · 2 retry", result.summary)
        assertEquals(2, result.latestEvents.size)
    }

    @Test
    fun nonTerminalHistoryDoesNotRequestFailureEmphasis() {
        val result = presentPlaybackFailureHistory(
            listOf(
                entry(
                    PlaybackFailureStreamKind.VIDEO,
                    4001,
                    PlaybackFailureHistoryOutcome.RETRYING,
                )
            )
        )!!

        assertFalse(result.hasTerminalFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun presentationRejectsZeroVisibleEvents() {
        presentPlaybackFailureHistory(
            listOf(
                entry(
                    PlaybackFailureStreamKind.VIDEO,
                    4001,
                    PlaybackFailureHistoryOutcome.RETRYING,
                )
            ),
            maxVisibleEvents = 0,
        )
    }

    private fun entry(
        stream: PlaybackFailureStreamKind,
        error: Int,
        outcome: PlaybackFailureHistoryOutcome,
    ) = PlaybackFailureHistoryEntry(
        streamKind = stream,
        errorCode = error,
        outcome = outcome,
        rendererFailure = true,
    )
}
