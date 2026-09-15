package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescueAdmissionTest {

    @Test
    fun firstPlatformFailureCanStartRescue() {
        assertEquals(
            AudioRuntimeRescueAdmission.ACCEPT,
            decideAudioRuntimeRescueAdmission(
                hasPendingPlan = false,
                rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST,
            ),
        )
    }

    @Test
    fun duplicateCallbackCannotReplacePendingRescue() {
        assertEquals(
            AudioRuntimeRescueAdmission.REJECT_PENDING_RESCUE,
            decideAudioRuntimeRescueAdmission(
                hasPendingPlan = true,
                rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST,
            ),
        )
    }

    @Test
    fun lateOldPlayerCallbackCannotStartSecondRescue() {
        assertEquals(
            AudioRuntimeRescueAdmission.REJECT_ALREADY_IN_RESCUE_MODE,
            decideAudioRuntimeRescueAdmission(
                hasPendingPlan = false,
                rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
            ),
        )
    }

    @Test
    fun pendingPlanWinsOverRendererModeForDeterministicRejection() {
        assertEquals(
            AudioRuntimeRescueAdmission.REJECT_PENDING_RESCUE,
            decideAudioRuntimeRescueAdmission(
                hasPendingPlan = true,
                rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
            ),
        )
    }
}
