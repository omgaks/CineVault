package com.sole.cinevault.glasses.halo

/**
 * D4-19 — presentation policy for the result of a Halo activation.
 *
 * Keeps activation outcome separate from rendering. Only a genuinely handled
 * click receives success feedback. Other outcomes stay visually quiet so Halo
 * does not imply that CineVault activated something when it did not.
 */
object HaloActivationFeedbackPolicy {

    fun resolve(
        outcome: HaloTargetActivationOutcome,
        visualPulseRequested: Boolean,
    ): HaloActivationFeedback {
        val showSuccessPulse =
            outcome == HaloTargetActivationOutcome.HANDLED &&
                visualPulseRequested

        return HaloActivationFeedback(
            showSuccessPulse = showSuccessPulse,
        )
    }
}

data class HaloActivationFeedback(
    val showSuccessPulse: Boolean,
)
