package com.sole.cinevault.picture

/**
 * P3 closure policy: adapts repair/chroma work to the existing Experience + Quality controls.
 *
 * No new persisted setting is introduced. The current PictureSettings remain the source of
 * truth; this policy derives safe GPU multipliers from content and intensity every frame.
 */
object PictureAdaptiveRepairPolicy {
    data class Multipliers(
        val repair: Float,
        val chroma: Float,
        val sharpenGuard: Float,
    )

    fun forState(
        content: PictureContent,
        intensity: Float,
    ): Multipliers {
        val quality = when {
            intensity < 0.72f -> PictureQualityTier.ECO
            intensity >= 0.96f -> PictureQualityTier.MAX
            else -> PictureQualityTier.BALANCED
        }

        val qualityRepair = when (quality) {
            PictureQualityTier.ECO -> 0.72f
            PictureQualityTier.BALANCED -> 0.90f
            PictureQualityTier.MAX -> 1.00f
        }
        val qualityChroma = when (quality) {
            PictureQualityTier.ECO -> 0.65f
            PictureQualityTier.BALANCED -> 0.86f
            PictureQualityTier.MAX -> 1.00f
        }

        val contentRepair = when (content) {
            PictureContent.ANIME -> 1.00f
            PictureContent.ANIMATION -> 0.94f
            PictureContent.FILM, PictureContent.AUTO -> 0.82f
        }
        val contentChroma = when (content) {
            PictureContent.ANIME -> 0.92f
            PictureContent.ANIMATION -> 1.00f
            PictureContent.FILM, PictureContent.AUTO -> 0.78f
        }
        val sharpenGuard = when (content) {
            PictureContent.ANIME -> 0.88f
            PictureContent.ANIMATION -> 0.84f
            PictureContent.FILM, PictureContent.AUTO -> 0.76f
        }

        return Multipliers(
            repair = (qualityRepair * contentRepair).coerceIn(0f, 1f),
            chroma = (qualityChroma * contentChroma).coerceIn(0f, 1f),
            sharpenGuard = sharpenGuard,
        )
    }
}
