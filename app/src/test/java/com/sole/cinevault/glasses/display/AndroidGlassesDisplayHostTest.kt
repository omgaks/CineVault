package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidGlassesDisplayHostTest {

    private class RecordingOperations : ExternalCineVaultDisplayOperations {
        val events = mutableListOf<String>()

        override fun show(displayId: Int) {
            events += "show:$displayId"
        }

        override fun dismiss() {
            events += "dismiss"
        }
    }

    @Test
    fun enter_showsRequestedExternalDisplay() {
        val ops = RecordingOperations()
        val host = AndroidGlassesDisplayHost(ops)

        host.enterExternalCineVault(31)

        assertEquals(listOf("show:31"), ops.events)
        assertEquals(31, host.activeDisplayId)
    }

    @Test
    fun duplicateEnter_isIdempotent() {
        val ops = RecordingOperations()
        val host = AndroidGlassesDisplayHost(ops)

        host.enterExternalCineVault(31)
        host.enterExternalCineVault(31)

        assertEquals(listOf("show:31"), ops.events)
    }

    @Test
    fun switch_dismissesOldSurfaceBeforeShowingNewOne() {
        val ops = RecordingOperations()
        val host = AndroidGlassesDisplayHost(ops)

        host.enterExternalCineVault(31)
        host.switchExternalCineVault(31, 32)

        assertEquals(
            listOf("show:31", "dismiss", "show:32"),
            ops.events,
        )
        assertEquals(32, host.activeDisplayId)
    }

    @Test
    fun exit_dismissesAndClearsOwnership() {
        val ops = RecordingOperations()
        val host = AndroidGlassesDisplayHost(ops)

        host.enterExternalCineVault(31)
        host.exitExternalCineVault()

        assertEquals(listOf("show:31", "dismiss"), ops.events)
        assertNull(host.activeDisplayId)
    }

    @Test
    fun repeatedExit_doesNotDismissTwice() {
        val ops = RecordingOperations()
        val host = AndroidGlassesDisplayHost(ops)

        host.enterExternalCineVault(31)
        host.exitExternalCineVault()
        host.exitExternalCineVault()

        assertEquals(listOf("show:31", "dismiss"), ops.events)
    }

    @Test
    fun release_cleansUpOwnedExternalSurface() {
        val ops = RecordingOperations()
        val host = AndroidGlassesDisplayHost(ops)

        host.enterExternalCineVault(31)
        host.release()

        assertEquals(listOf("show:31", "dismiss"), ops.events)
        assertNull(host.activeDisplayId)
    }
}
