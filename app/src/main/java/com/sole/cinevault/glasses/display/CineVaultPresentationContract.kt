package com.sole.cinevault.glasses.display

/**
 * D6-1 — One-CineVault presentation contract.
 *
 * This contract makes the architectural rule explicit:
 * an external display is another presentation target for the SAME CineVault
 * feature/state tree. It is never a request to create a glasses-specific copy
 * of player, subtitles, Audio Studio, Sub Studio, FFmpeg state, or navigation.
 */
object CineVaultPresentationContract {

    fun resolve(
        renderPlan: CineVaultDisplayRenderPlan,
    ): CineVaultPresentationDecision {
        val external = renderPlan.external

        return if (
            external?.content == CineVaultRenderContent.FULL_CINEVAULT
        ) {
            CineVaultPresentationDecision(
                host = CineVaultPresentationRole.CINEMA_VOID_CONTROLLER,
                external = CineVaultPresentationRole.SHARED_CINEVAULT,
                sharesFeatureState = true,
                allowsGlassesSpecificFeatureTree = false,
            )
        } else {
            CineVaultPresentationDecision(
                host = CineVaultPresentationRole.SHARED_CINEVAULT,
                external = CineVaultPresentationRole.NONE,
                sharesFeatureState = true,
                allowsGlassesSpecificFeatureTree = false,
            )
        }
    }
}

data class CineVaultPresentationDecision(
    val host: CineVaultPresentationRole,
    val external: CineVaultPresentationRole,
    val sharesFeatureState: Boolean,
    val allowsGlassesSpecificFeatureTree: Boolean,
)

enum class CineVaultPresentationRole {
    SHARED_CINEVAULT,
    CINEMA_VOID_CONTROLLER,
    NONE,
}
