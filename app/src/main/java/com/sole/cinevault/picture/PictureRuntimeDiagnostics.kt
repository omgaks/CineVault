package com.sole.cinevault.picture

/**
 * Human-readable P1 diagnostics for future Picture UI/debug surfaces.
 * No rendering behavior depends on these strings.
 */
data class PictureRuntimeDiagnostics(
    val content: String,
    val requestedTier: String,
    val effectiveTier: String,
    val state: String,
    val activeStages: List<String>,
)

fun PictureRuntimeState.toDiagnostics(): PictureRuntimeDiagnostics =
    PictureRuntimeDiagnostics(
        content = profile.content.name,
        requestedTier = profile.qualityTier.name,
        effectiveTier = effectiveTier?.name ?: "BYPASS",
        state = bypassReason ?: "Ready",
        activeStages = enabledStages.map { it.name },
    )
