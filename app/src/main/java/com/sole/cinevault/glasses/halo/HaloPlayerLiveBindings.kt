package com.sole.cinevault.glasses.halo

/**
 * D5-4 — live callback ownership for player integration.
 *
 * Centralizes the final callback gates used by PlayerPlaybackGestureLayer.
 * It deliberately does not know any device model or fixed window size.
 */
class HaloPlayerLiveBindings(
    private val bridge: HaloPlayerInputBridge,
    private val moveCanonicalPointer: (x: Float, y: Float) -> Unit,
    private val clickCanonicalTarget: () -> Boolean,
) {
    fun movePointer(x: Float, y: Float) {
        if (bridge.canonicalPointerEnabled) {
            moveCanonicalPointer(x, y)
        }
    }

    fun clickTarget(): Boolean {
        if (!bridge.canonicalClickEnabled) return false
        return clickCanonicalTarget()
    }

    /**
     * When a floating/transient player surface is visible, the canonical
     * target surface keeps priority. The video background must not reinterpret
     * the same tap as a control-toggle/dismiss gesture.
     */
    fun backgroundTapMayToggleControls(): Boolean =
        !bridge.transientUiHasPriority
}
