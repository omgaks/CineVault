package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloActivationFeedbackPolicyTest {

    @Test fun handledActivationWithRequestedPulseShowsSuccess() {
        assertTrue(
            HaloActivationFeedbackPolicy.resolve(
                outcome = HaloTargetActivationOutcome.HANDLED,
                visualPulseRequested = true,
            ).showSuccessPulse
        )
    }

    @Test fun handledActivationCanOptOutOfPulse() {
        assertFalse(
            HaloActivationFeedbackPolicy.resolve(
                outcome = HaloTargetActivationOutcome.HANDLED,
                visualPulseRequested = false,
            ).showSuccessPulse
        )
    }

    @Test fun unhandledActivationNeverShowsSuccess() {
        assertFalse(
            HaloActivationFeedbackPolicy.resolve(
                outcome = HaloTargetActivationOutcome.UNHANDLED,
                visualPulseRequested = true,
            ).showSuccessPulse
        )
    }

    @Test fun blockedActivationNeverShowsSuccess() {
        assertFalse(
            HaloActivationFeedbackPolicy.resolve(
                outcome = HaloTargetActivationOutcome.BLOCKED,
                visualPulseRequested = true,
            ).showSuccessPulse
        )
    }

    @Test fun ignoredOrNotAttemptedNeverShowsSuccess() {
        assertFalse(
            HaloActivationFeedbackPolicy.resolve(
                outcome = HaloTargetActivationOutcome.IGNORED,
                visualPulseRequested = true,
            ).showSuccessPulse
        )
        assertFalse(
            HaloActivationFeedbackPolicy.resolve(
                outcome = HaloTargetActivationOutcome.NOT_ATTEMPTED,
                visualPulseRequested = true,
            ).showSuccessPulse
        )
    }
}
