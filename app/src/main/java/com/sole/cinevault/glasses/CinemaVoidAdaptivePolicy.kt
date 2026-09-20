package com.sole.cinevault.glasses

/**
 * Window-based Cinema Void policy.
 *
 * These are available-window profiles, not device categories. The same
 * physical device may move between profiles after rotation, split-screen,
 * freeform resize or fold/unfold.
 */
enum class CinemaVoidWindowProfile {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

data class CinemaVoidAdaptiveSpec(
    val profile: CinemaVoidWindowProfile,
    val edgeGestureFraction: Float,
    val seekGestureFraction: Float,
    val haloSurfaceFraction: Float,
    val statusCardWidthFraction: Float,
    val statusCardMaxWidthDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
)

object CinemaVoidAdaptivePolicy {
    const val MEDIUM_WIDTH_DP = 600
    const val EXPANDED_WIDTH_DP = 840

    fun resolve(
        availableWidthDp: Int,
        availableHeightDp: Int,
    ): CinemaVoidAdaptiveSpec {
        val safeWidth = availableWidthDp.coerceAtLeast(1)
        val safeHeight = availableHeightDp.coerceAtLeast(1)
        val shortestAvailableEdge = minOf(safeWidth, safeHeight)

        val profile =
            when {
                safeWidth >= EXPANDED_WIDTH_DP ->
                    CinemaVoidWindowProfile.EXPANDED
                safeWidth >= MEDIUM_WIDTH_DP ->
                    CinemaVoidWindowProfile.MEDIUM
                else ->
                    CinemaVoidWindowProfile.COMPACT
            }

        val padding =
            when (profile) {
                CinemaVoidWindowProfile.COMPACT -> 12
                CinemaVoidWindowProfile.MEDIUM -> 18
                CinemaVoidWindowProfile.EXPANDED -> 24
            }

        val statusMaxWidth =
            when (profile) {
                CinemaVoidWindowProfile.COMPACT -> 360
                CinemaVoidWindowProfile.MEDIUM -> 460
                CinemaVoidWindowProfile.EXPANDED -> 560
            }

        val statusWidthFraction =
            when {
                shortestAvailableEdge < 360 -> 0.92f
                profile == CinemaVoidWindowProfile.COMPACT -> 0.88f
                profile == CinemaVoidWindowProfile.MEDIUM -> 0.72f
                else -> 0.56f
            }

        return CinemaVoidAdaptiveSpec(
            profile = profile,
            edgeGestureFraction = 0.20f,
            seekGestureFraction = 0.50f,
            haloSurfaceFraction = 1.00f,
            statusCardWidthFraction = statusWidthFraction,
            statusCardMaxWidthDp = statusMaxWidth,
            horizontalPaddingDp = padding,
            verticalPaddingDp = padding,
        )
    }
}
