package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesDisplayLifecycleControllerTest {

    private class RecordingHost : GlassesDisplayLifecycleHost {
        val events = mutableListOf<String>()

        override fun enterExternalCineVault(displayId: Int) {
            events += "enter:$displayId"
        }

        override fun switchExternalCineVault(fromDisplayId: Int?, toDisplayId: Int) {
            events += "switch:$fromDisplayId->$toDisplayId"
        }

        override fun exitExternalCineVault() {
            events += "exit"
        }
    }

    @Test
    fun repeatedSameState_doesNotRepeatHostWork() {
        val host = RecordingHost()
        val controller = GlassesDisplayLifecycleController(host)
        val state = resolveGlassesDisplayMode(true, 21, "RayNeo")

        controller.apply(state)
        controller.apply(state)
        controller.apply(state)

        assertEquals(listOf("enter:21"), host.events)
    }

    @Test
    fun displayReplacement_switchesInPlace() {
        val host = RecordingHost()
        val controller = GlassesDisplayLifecycleController(host)

        controller.apply(resolveGlassesDisplayMode(true, 21, "RayNeo"))
        controller.apply(resolveGlassesDisplayMode(true, 22, "RayNeo"))

        assertEquals(
            listOf("enter:21", "switch:21->22"),
            host.events,
        )
        assertEquals(22, controller.snapshot.externalDisplayId)
    }

    @Test
    fun disconnect_exitsAndClearsSnapshot() {
        val host = RecordingHost()
        val controller = GlassesDisplayLifecycleController(host)

        controller.apply(resolveGlassesDisplayMode(true, 21, "RayNeo"))
        controller.apply(resolveGlassesDisplayMode(false, null, null))

        assertEquals(listOf("enter:21", "exit"), host.events)
        assertEquals(GlassesDisplayLifecycleSnapshot(), controller.snapshot)
    }

    @Test
    fun reconnect_entersAgain() {
        val host = RecordingHost()
        val controller = GlassesDisplayLifecycleController(host)

        controller.apply(resolveGlassesDisplayMode(true, 21, "RayNeo"))
        controller.apply(resolveGlassesDisplayMode(false, null, null))
        controller.apply(resolveGlassesDisplayMode(true, 21, "RayNeo"))

        assertEquals(
            listOf("enter:21", "exit", "enter:21"),
            host.events,
        )
    }

    @Test
    fun reset_forgetsOwnershipWithoutCallingHost() {
        val host = RecordingHost()
        val controller = GlassesDisplayLifecycleController(host)

        controller.apply(resolveGlassesDisplayMode(true, 21, "RayNeo"))
        controller.reset()

        assertEquals(listOf("enter:21"), host.events)
        assertEquals(GlassesDisplayLifecycleSnapshot(), controller.snapshot)
    }
}
