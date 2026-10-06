package com.sole.cinevault.picture

/**
 * P4-S1 Anime Engine policy.
 *
 * Runtime-only: no new persisted setting. Anime gets a conservative line-art pass while
 * Animation and Film remain on the established P3 path.
 */
object PictureAnimeEnginePolicy {
    data class Params(
        val enabled: Float,
        val lineStrength: Float,
        val flatProtection: Float,
        val haloGuard: Float,
    )

    fun forState(content: PictureContent, intensity: Float): Params {
        if (content != PictureContent.ANIME) {
            return Params(0f, 0f, 1f, 1f)
        }
        val qualityScale = when {
            intensity < 0.72f -> 0.58f
            intensity >= 0.96f -> 1.00f
            else -> 0.82f
        }
        return Params(
            enabled = 1f,
            lineStrength = (0.34f * qualityScale).coerceIn(0f, 0.34f),
            flatProtection = 0.90f,
            haloGuard = 0.78f,
        )
    }
}
