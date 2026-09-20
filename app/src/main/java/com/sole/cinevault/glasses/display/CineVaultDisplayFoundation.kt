package com.sole.cinevault.glasses.display

/**
 * D1-20 — Display foundation closure contract.
 *
 * D1 is complete when CineVault is treated as one logical app/session rendered
 * on more than one physical display. This file intentionally adds no new UI,
 * player, navigation stack, subtitle panel, gesture system or glasses feature.
 *
 * Future phases must build ON this foundation rather than fork CineVault.
 */
object CineVaultDisplayFoundation {

    const val VERSION = 1

    val invariants: Set<CineVaultDisplayInvariant> = setOf(
        CineVaultDisplayInvariant.ONE_LOGICAL_SESSION,
        CineVaultDisplayInvariant.CANONICAL_CINEVAULT_UI,
        CineVaultDisplayInvariant.SHARED_TOP_LEVEL_STATE,
        CineVaultDisplayInvariant.CANONICAL_ROUTE_PROJECTION,
        CineVaultDisplayInvariant.NO_GLASSES_ONLY_PLAYER,
        CineVaultDisplayInvariant.DISPLAY_FEATURES_ARE_ADDITIVE,
    )

    fun supports(invariant: CineVaultDisplayInvariant): Boolean =
        invariant in invariants
}

enum class CineVaultDisplayInvariant {
    /** Tablet and external display belong to one CineVault render session. */
    ONE_LOGICAL_SESSION,

    /** Both surfaces render the existing CineVault UI rather than a clone. */
    CANONICAL_CINEVAULT_UI,

    /** Safe top-level state such as tab/search is session-owned. */
    SHARED_TOP_LEVEL_STATE,

    /** Existing CineVault routes are projected into the display session. */
    CANONICAL_ROUTE_PROJECTION,

    /** Never create a separate glasses player/control/subtitle application. */
    NO_GLASSES_ONLY_PLAYER,

    /** Halo, Cinema Void, scaling, 3D, etc. extend CineVault; they don't fork it. */
    DISPLAY_FEATURES_ARE_ADDITIVE,
}

/**
 * Tiny gate for later phases. It makes architectural drift explicit in code:
 * a future display feature declares that it is additive to canonical CineVault.
 */
data class CineVaultDisplayFeatureContract(
    val name: String,
    val additiveToCanonicalUi: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Display feature name must not be blank." }
        require(additiveToCanonicalUi) {
            "Display features must extend canonical CineVault, not replace it."
        }
    }
}
