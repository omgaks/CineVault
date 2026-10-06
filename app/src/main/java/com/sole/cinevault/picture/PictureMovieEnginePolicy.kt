package com.sole.cinevault.picture

/**
 * P6 Movie Engine core policy.
 *
 * S1 is deliberately policy-only: it defines a conservative live-action restoration envelope
 * without changing Media3 effect topology or the shader. The existing single Picture effect
 * remains the only full-frame GPU pass.
 *
 * Goals:
 * - preserve natural film grain and fine texture;
 * - avoid halos and oversharpening on already-clean HD/UHD sources;
 * - allow stronger recovery only where lower-resolution sources benefit;
 * - keep skin/chroma protection high for live-action material;
 * - HDR/Dolby Vision behavior remains unchanged and is still refused by PictureEnhanceEffect.
 */
object PictureMovieEnginePolicy {

    data class Params(
        val enabled: Float,
        val detailRecovery: Float,
        val textureProtection: Float,
        val grainProtection: Float,
        val skinProtection: Float,
        val haloGuard: Float,
        val chromaGuard: Float,
        val sharpenCeiling: Float,
    )

    fun forState(
        content: PictureContent,
        intensity: Float,
        sourceHeight: Int = 1080,
    ): Params {
        if (content != PictureContent.FILM) return disabled()

        val qualityScale = when {
            intensity < 0.72f -> 0.52f   // Eco
            intensity >= 0.96f -> 1.00f  // Max
            else -> 0.78f                // Balanced
        }

        // Resolution is used only as a restoration-strength envelope.
        // It does not classify the movie or infer how it was produced.
        val sourceScale = when {
            sourceHeight <= 576 -> 1.00f
            sourceHeight <= 720 -> 0.82f
            sourceHeight <= 1080 -> 0.58f
            else -> 0.30f
        }

        val cleanSourceBias = when {
            sourceHeight <= 576 -> 0.00f
            sourceHeight <= 720 -> 0.12f
            sourceHeight <= 1080 -> 0.28f
            else -> 0.48f
        }

        return Params(
            enabled = 1f,
            detailRecovery = (0.22f * qualityScale * sourceScale).coerceIn(0f, 0.22f),
            textureProtection = (0.84f + 0.12f * cleanSourceBias).coerceIn(0.84f, 0.96f),
            grainProtection = (0.86f + 0.10f * cleanSourceBias).coerceIn(0.86f, 0.96f),
            skinProtection = 0.94f,
            haloGuard = (0.86f + 0.10f * cleanSourceBias).coerceIn(0.86f, 0.96f),
            chromaGuard = (0.90f + 0.07f * cleanSourceBias).coerceIn(0.90f, 0.97f),
            sharpenCeiling = (0.26f - 0.08f * cleanSourceBias).coerceIn(0.18f, 0.26f),
        )
    }

    private fun disabled() = Params(
        enabled = 0f,
        detailRecovery = 0f,
        textureProtection = 1f,
        grainProtection = 1f,
        skinProtection = 1f,
        haloGuard = 1f,
        chromaGuard = 1f,
        sharpenCeiling = 0f,
    )
}
