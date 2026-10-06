package com.sole.cinevault.picture

/**
 * P4 Anime Engine policy + P5 Animation routing bridge.
 *
 * P4 Anime values are preserved. P5 Animation uses the same proven single-pass GPU execution
 * primitive but receives a separate, more conservative animation policy. This keeps the
 * Media3 effect topology unchanged and avoids stacking another expensive full-frame pass.
 */
object PictureAnimeEnginePolicy {
    data class Params(
        val enabled: Float,
        val lineStrength: Float,
        val flatProtection: Float,
        val haloGuard: Float,
        val reconstruction: Float,
        val diagonalAssist: Float,
        val chromaEdgeGuard: Float,
    )

    fun forState(
        content: PictureContent,
        intensity: Float,
        sourceHeight: Int = 1080,
    ): Params {
        if (content == PictureContent.ANIMATION) {
            val animation = PictureAnimationEnginePolicy.forState(
                content = content,
                intensity = intensity,
                sourceHeight = sourceHeight,
            )
            return Params(
                enabled = animation.enabled,
                lineStrength = animation.lineStrength,
                flatProtection = animation.flatProtection,
                haloGuard = animation.haloGuard,
                reconstruction = animation.reconstruction,
                diagonalAssist = animation.diagonalAssist,
                chromaEdgeGuard = animation.chromaEdgeGuard,
            )
        }

        if (content != PictureContent.ANIME) {
            return Params(0f, 0f, 1f, 1f, 0f, 0f, 1f)
        }

        // P4 values intentionally unchanged.
        val qualityScale = when {
            intensity < 0.72f -> 0.58f
            intensity >= 0.96f -> 1.00f
            else -> 0.82f
        }
        val sourceScale = when {
            sourceHeight <= 576 -> 1.00f
            sourceHeight <= 720 -> 0.90f
            sourceHeight <= 1080 -> 0.68f
            else -> 0.42f
        }

        return Params(
            enabled = 1f,
            lineStrength = (0.34f * qualityScale).coerceIn(0f, 0.34f),
            flatProtection = 0.90f,
            haloGuard = 0.78f,
            reconstruction = (0.30f * qualityScale * sourceScale).coerceIn(0f, 0.30f),
            diagonalAssist = (0.24f * qualityScale * sourceScale).coerceIn(0f, 0.24f),
            chromaEdgeGuard = 0.82f,
        )
    }
}
