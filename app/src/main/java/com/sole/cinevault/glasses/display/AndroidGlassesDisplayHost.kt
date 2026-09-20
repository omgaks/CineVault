package com.sole.cinevault.glasses.display

/**
 * D1-7: concrete lifecycle host used at the Android integration boundary.
 *
 * Presentation creation/dismissal is intentionally injected as operations.
 * That lets the current working glasses Presentation remain the fallback while
 * later slices introduce the shared CineVault external renderer.
 *
 * No player, navigation, subtitle, or menu state lives here.
 */
interface ExternalCineVaultDisplayOperations {
    fun show(displayId: Int)
    fun dismiss()
}

class AndroidGlassesDisplayHost(
    private val operations: ExternalCineVaultDisplayOperations,
) : GlassesDisplayLifecycleHost {

    var activeDisplayId: Int? = null
        private set

    override fun enterExternalCineVault(displayId: Int) {
        if (activeDisplayId == displayId) return

        if (activeDisplayId != null) {
            operations.dismiss()
        }

        operations.show(displayId)
        activeDisplayId = displayId
    }

    override fun switchExternalCineVault(
        fromDisplayId: Int?,
        toDisplayId: Int,
    ) {
        if (activeDisplayId == toDisplayId) return

        if (activeDisplayId != null || fromDisplayId != null) {
            operations.dismiss()
        }

        operations.show(toDisplayId)
        activeDisplayId = toDisplayId
    }

    override fun exitExternalCineVault() {
        if (activeDisplayId == null) return

        operations.dismiss()
        activeDisplayId = null
    }

    /**
     * Activity/host teardown helper. The external window must not outlive its
     * Android owner. This is cleanup, not a change to CineVault playback state.
     */
    fun release() {
        if (activeDisplayId != null) {
            operations.dismiss()
        }
        activeDisplayId = null
    }
}
