package com.sole.cinevault.glasses

/**
 * Pure lifecycle policy for external-display handoff.
 *
 * A physical disconnect always returns playback ownership to the host.
 * A new valid display id starts a fresh external-display session automatically.
 * A user-disabled session stays disabled only for that same connected display.
 */
data class ExternalDisplayLifecycleState(
    val connectedDisplayId: Int?,
    val disabledDisplayId: Int? = null,
) {
    val isConnected: Boolean get() = connectedDisplayId != null
    val isSessionEnabled: Boolean
        get() = connectedDisplayId != null && disabledDisplayId != connectedDisplayId
}

object ExternalDisplayLifecyclePolicy {
    fun onDisplayChanged(
        previous: ExternalDisplayLifecycleState,
        newDisplayId: Int?,
    ): ExternalDisplayLifecycleState {
        if (newDisplayId == null) {
            return ExternalDisplayLifecycleState(
                connectedDisplayId = null,
                disabledDisplayId = null,
            )
        }

        if (newDisplayId != previous.connectedDisplayId) {
            return ExternalDisplayLifecycleState(
                connectedDisplayId = newDisplayId,
                disabledDisplayId = null,
            )
        }

        return previous.copy(connectedDisplayId = newDisplayId)
    }

    fun disableCurrentSession(
        state: ExternalDisplayLifecycleState,
    ): ExternalDisplayLifecycleState =
        if (state.connectedDisplayId == null) {
            state
        } else {
            state.copy(disabledDisplayId = state.connectedDisplayId)
        }
}
