package com.sole.cinevault.glasses.halo

/**
 * D4-17 — outcome contract for a permitted Halo activation.
 *
 * The activation policy decides whether delivery may be attempted.
 * This resolver records what actually happened after Android's normal input
 * pipeline receives the synthetic click. A permitted click is not automatically
 * treated as successful: dispatchTouchEvent() may report that no target handled it.
 */
object HaloTargetActivationOutcomeResolver {

    fun resolve(
        decision: HaloTargetActivationDecision,
        dispatchHandled: Boolean?,
    ): HaloTargetActivationOutcome {
        return when (decision) {
            HaloTargetActivationDecision.DISPATCH -> {
                when (dispatchHandled) {
                    true -> HaloTargetActivationOutcome.HANDLED
                    false -> HaloTargetActivationOutcome.UNHANDLED
                    null -> HaloTargetActivationOutcome.NOT_ATTEMPTED
                }
            }

            HaloTargetActivationDecision.IGNORE ->
                HaloTargetActivationOutcome.IGNORED

            HaloTargetActivationDecision.BLOCK_DRAG,
            HaloTargetActivationDecision.BLOCK_OUTSIDE,
            HaloTargetActivationDecision.BLOCK_UNAVAILABLE ->
                HaloTargetActivationOutcome.BLOCKED
        }
    }
}

enum class HaloTargetActivationOutcome {
    HANDLED,
    UNHANDLED,
    BLOCKED,
    IGNORED,
    NOT_ATTEMPTED,
}
