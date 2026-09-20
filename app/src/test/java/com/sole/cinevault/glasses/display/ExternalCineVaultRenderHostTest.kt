package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalCineVaultRenderHostTest {

    private class RecordingRenderer : ExternalCineVaultRenderer {
        val events = mutableListOf<String>()

        override fun attach(displayId: Int) {
            events += "attach:$displayId"
        }

        override fun detach() {
            events += "detach"
        }
    }

    @Test
    fun show_attachesSharedRenderer() {
        val renderer = RecordingRenderer()
        val host = ExternalCineVaultRenderHost(renderer)

        host.show(41)

        assertEquals(listOf("attach:41"), renderer.events)
        assertEquals(41, host.attachedDisplayId)
    }

    @Test
    fun sameDisplay_doesNotAttachTwice() {
        val renderer = RecordingRenderer()
        val host = ExternalCineVaultRenderHost(renderer)

        host.show(41)
        host.show(41)

        assertEquals(listOf("attach:41"), renderer.events)
    }

    @Test
    fun changedDisplay_detachesBeforeReattach() {
        val renderer = RecordingRenderer()
        val host = ExternalCineVaultRenderHost(renderer)

        host.show(41)
        host.show(42)

        assertEquals(
            listOf("attach:41", "detach", "attach:42"),
            renderer.events,
        )
        assertEquals(42, host.attachedDisplayId)
    }

    @Test
    fun dismiss_detachesExactlyOnce() {
        val renderer = RecordingRenderer()
        val host = ExternalCineVaultRenderHost(renderer)

        host.show(41)
        host.dismiss()
        host.dismiss()

        assertEquals(listOf("attach:41", "detach"), renderer.events)
        assertNull(host.attachedDisplayId)
    }

    @Test
    fun unifiedRuntime_connectSameDisplayDisconnect_hasSingleOwnershipFlow() {
        val renderer = RecordingRenderer()
        val runtime = UnifiedGlassesDisplayRuntime(renderer)

        runtime.apply(resolveGlassesDisplayMode(true, 41, "RayNeo"))
        runtime.apply(resolveGlassesDisplayMode(true, 41, "RayNeo"))
        runtime.apply(resolveGlassesDisplayMode(false, null, null))

        assertEquals(
            listOf("attach:41", "detach"),
            renderer.events,
        )
        assertEquals(
            GlassesDisplayLifecycleSnapshot(),
            runtime.snapshot,
        )
    }

    @Test
    fun unifiedRuntime_switchesExternalDisplayWithoutDuplicateRendererState() {
        val renderer = RecordingRenderer()
        val runtime = UnifiedGlassesDisplayRuntime(renderer)

        runtime.apply(resolveGlassesDisplayMode(true, 41, "RayNeo"))
        runtime.apply(resolveGlassesDisplayMode(true, 42, "RayNeo"))

        assertEquals(
            listOf("attach:41", "detach", "attach:42"),
            renderer.events,
        )
        assertEquals(42, runtime.snapshot.externalDisplayId)
    }

    @Test
    fun release_isSafeAndClearsRuntime() {
        val renderer = RecordingRenderer()
        val runtime = UnifiedGlassesDisplayRuntime(renderer)

        runtime.apply(resolveGlassesDisplayMode(true, 41, "RayNeo"))
        runtime.release()
        runtime.release()

        assertEquals(listOf("attach:41", "detach"), renderer.events)
        assertEquals(
            GlassesDisplayLifecycleSnapshot(),
            runtime.snapshot,
        )
    }
}
