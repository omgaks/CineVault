package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRescueTrackRestoreDecisionTest {

    @Test
    fun matchingTrackFinishesImmediately() {
        assertEquals(
            AudioRescueTrackRestoreDecision.RESTORED,
            audioRescueTrackRestoreDecision(
                matchingTrackFound = true,
                audioGroupsPresent = true,
                trackEventsSeen = 1,
            ),
        )
    }

    @Test
    fun noAudioGroupsKeepsWaiting() {
        assertEquals(
            AudioRescueTrackRestoreDecision.KEEP_WAITING,
            audioRescueTrackRestoreDecision(
                matchingTrackFound = false,
                audioGroupsPresent = false,
                trackEventsSeen = 3,
            ),
        )
    }

    @Test
    fun earlyAudioTrackUpdateKeepsWaitingForExactIdentity() {
        assertEquals(
            AudioRescueTrackRestoreDecision.KEEP_WAITING,
            audioRescueTrackRestoreDecision(
                matchingTrackFound = false,
                audioGroupsPresent = true,
                trackEventsSeen = 2,
            ),
        )
    }

    @Test
    fun repeatedAudioTrackUpdatesWithoutMatchGiveUpCleanly() {
        assertEquals(
            AudioRescueTrackRestoreDecision.GIVE_UP,
            audioRescueTrackRestoreDecision(
                matchingTrackFound = false,
                audioGroupsPresent = true,
                trackEventsSeen = 3,
            ),
        )
    }
}
