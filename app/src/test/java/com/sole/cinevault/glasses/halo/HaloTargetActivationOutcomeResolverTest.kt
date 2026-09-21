package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloTargetActivationOutcomeResolverTest {

    @Test fun dispatchedAndHandledBecomesHandled() {
        assertEquals(
            HaloTargetActivationOutcome.HANDLED,
            HaloTargetActivationOutcomeResolver.resolve(
                HaloTargetActivationDecision.DISPATCH,
                dispatchHandled = true,
            ),
        )
    }

    @Test fun dispatchedButUnhandledIsNotSuccess() {
        assertEquals(
            HaloTargetActivationOutcome.UNHANDLED,
            HaloTargetActivationOutcomeResolver.resolve(
                HaloTargetActivationDecision.DISPATCH,
                dispatchHandled = false,
            ),
        )
    }

    @Test fun blockedDecisionNeverReportsHandled() {
        assertEquals(
            HaloTargetActivationOutcome.BLOCKED,
            HaloTargetActivationOutcomeResolver.resolve(
                HaloTargetActivationDecision.BLOCK_OUTSIDE,
                dispatchHandled = null,
            ),
        )
    }

    @Test fun ignoredDecisionStaysIgnored() {
        assertEquals(
            HaloTargetActivationOutcome.IGNORED,
            HaloTargetActivationOutcomeResolver.resolve(
                HaloTargetActivationDecision.IGNORE,
                dispatchHandled = null,
            ),
        )
    }

    @Test fun dispatchWithoutAttemptIsExplicit() {
        assertEquals(
            HaloTargetActivationOutcome.NOT_ATTEMPTED,
            HaloTargetActivationOutcomeResolver.resolve(
                HaloTargetActivationDecision.DISPATCH,
                dispatchHandled = null,
            ),
        )
    }
}
