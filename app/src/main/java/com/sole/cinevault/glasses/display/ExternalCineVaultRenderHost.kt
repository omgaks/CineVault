package com.sole.cinevault.glasses.display

/**
 * D1-8: renderer ownership seam for the external CineVault display.
 *
 * The renderer supplied here must render the SAME CineVault experience/state
 * used by the host app. This class owns only external-surface lifecycle.
 *
 * It intentionally knows nothing about movies, ExoPlayer, subtitles, menus,
 * navigation, Halo, or glasses-specific player controls.
 */
interface ExternalCineVaultRenderer {
    fun attach(displayId: Int)
    fun detach()
}

/**
 * Concrete implementation of the D1-7 operations boundary.
 *
 * AndroidGlassesDisplayHost -> ExternalCineVaultRenderHost -> shared renderer
 */
class ExternalCineVaultRenderHost(
    private val renderer: ExternalCineVaultRenderer,
) : ExternalCineVaultDisplayOperations {

    var attachedDisplayId: Int? = null
        private set

    override fun show(displayId: Int) {
        if (attachedDisplayId == displayId) return

        if (attachedDisplayId != null) {
            renderer.detach()
        }

        renderer.attach(displayId)
        attachedDisplayId = displayId
    }

    override fun dismiss() {
        if (attachedDisplayId == null) return

        renderer.detach()
        attachedDisplayId = null
    }

    /**
     * Explicit owner teardown. Safe to call repeatedly.
     */
    fun release() {
        dismiss()
    }
}

/**
 * Complete D1 lifecycle stack.
 *
 * Later Android integration only needs to feed [GlassesDisplayModeState] into
 * [apply]. The renderer remains injected, preventing this layer from growing a
 * second CineVault implementation.
 */
class UnifiedGlassesDisplayRuntime(
    renderer: ExternalCineVaultRenderer,
) {
    private val renderHost = ExternalCineVaultRenderHost(renderer)
    private val androidHost = AndroidGlassesDisplayHost(renderHost)
    private val lifecycleController = GlassesDisplayLifecycleController(androidHost)

    val snapshot: GlassesDisplayLifecycleSnapshot
        get() = lifecycleController.snapshot

    fun apply(
        displayModeState: GlassesDisplayModeState,
    ): GlassesDisplayLifecycleDecision =
        lifecycleController.apply(displayModeState)

    fun release() {
        androidHost.release()
        lifecycleController.reset()
        renderHost.release()
    }
}
