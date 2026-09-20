package com.sole.cinevault.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayLifecyclePolicyTest {

    @Test
    fun disconnectClearsDisabledSessionAndReturnsToHostState() {
        val disabled = ExternalDisplayLifecycleState(
            connectedDisplayId = 7,
            disabledDisplayId = 7,
        )

        val disconnected =
            ExternalDisplayLifecyclePolicy.onDisplayChanged(
                previous = disabled,
                newDisplayId = null,
            )

        assertFalse(disconnected.isConnected)
        assertFalse(disconnected.isSessionEnabled)
        assertEquals(null, disconnected.disabledDisplayId)
    }

    @Test
    fun sameDisplayRemainsDisabledUntilPhysicalDisplayChanges() {
        val disabled = ExternalDisplayLifecycleState(
            connectedDisplayId = 7,
            disabledDisplayId = 7,
        )

        val same =
            ExternalDisplayLifecyclePolicy.onDisplayChanged(
                previous = disabled,
                newDisplayId = 7,
            )

        assertFalse(same.isSessionEnabled)
        assertEquals(7, same.disabledDisplayId)
    }

    @Test
    fun reconnectWithNewDisplayIdStartsFreshEnabledSession() {
        val old = ExternalDisplayLifecycleState(
            connectedDisplayId = 7,
            disabledDisplayId = 7,
        )

        val reconnected =
            ExternalDisplayLifecyclePolicy.onDisplayChanged(
                previous = old,
                newDisplayId = 12,
            )

        assertTrue(reconnected.isConnected)
        assertTrue(reconnected.isSessionEnabled)
        assertEquals(null, reconnected.disabledDisplayId)
    }

    @Test
    fun disableCurrentSessionOnlyDisablesConnectedDisplay() {
        val connected = ExternalDisplayLifecycleState(connectedDisplayId = 12)

        val disabled =
            ExternalDisplayLifecyclePolicy.disableCurrentSession(connected)

        assertEquals(12, disabled.disabledDisplayId)
        assertFalse(disabled.isSessionEnabled)
    }
}
