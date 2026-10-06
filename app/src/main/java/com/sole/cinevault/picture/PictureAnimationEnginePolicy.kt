package com.sole.cinevault.picture

/**
 * P5 Animation Engine policy.
 *
 * The existing P4 GPU line/reconstruction stage is deliberately reused as the execution
 * primitive so P5 does not add another Media3 effect or another full-frame pass.
 *
 * Animation is tuned differently from Anime:
 * - lower line reinforcement, because CGI/stylised animation often has softer edges;
 * - stronger flat-region protection, preserving intentional painted/CGI fills;
 * - conservative reconstruction that scales down as source resolution rises;
 * - stronger halo/chroma guards to avoid coloured-edge ringing;
 * - quality tiers remain Eco / Balanced / Max through the existing intensity control.
 *
 * This policy is runtime-only. Nothing new is persisted.
 */
object PictureAnimationEnginePolicy {

    enum class Profile {
        CLASSIC_2D,
        STYLIZED,
        CGI,
    }

    data class Params(
        val enabled: Float,
        val profile: Profile,
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
        if (content != PictureContent.ANIMATION) {
            return disabled()
        }

        val qualityScale = when {
            intensity < 0.72f -> 0.52f
            intensity >= 0.96f -> 1.00f
            else -> 0.78f
        }

        val sourceScale = when {
            sourceHeight <= 576 -> 1.00f
            sourceHeight <= 720 -> 0.86f
            sourceHeight <= 1080 -> 0.60f
            else -> 0.34f
        }

        /*
         * CineVault does not yet persist a separate animation-subtype metadata field.
         * Use source resolution as a conservative runtime profile hint rather than inventing
         * metadata:
         *   SD material -> classic 2D restoration bias
         *   HD material -> stylised/mixed animation bias
         *   1080p+     -> CGI/modern animation preservation bias
         *
         * The shader remains adaptive per pixel, so this is only a strength envelope.
         */
        val profile = when {
            sourceHeight <= 576 -> Profile.CLASSIC_2D
            sourceHeight <= 900 -> Profile.STYLIZED
            else -> Profile.CGI
        }

        val profileLine = when (profile) {
            Profile.CLASSIC_2D -> 0.25f
            Profile.STYLIZED -> 0.20f
            Profile.CGI -> 0.13f
        }
        val profileReconstruction = when (profile) {
            Profile.CLASSIC_2D -> 0.24f
            Profile.STYLIZED -> 0.18f
            Profile.CGI -> 0.10f
        }
        val profileDiagonal = when (profile) {
            Profile.CLASSIC_2D -> 0.18f
            Profile.STYLIZED -> 0.14f
            Profile.CGI -> 0.08f
        }
        val flatProtection = when (profile) {
            Profile.CLASSIC_2D -> 0.93f
            Profile.STYLIZED -> 0.95f
            Profile.CGI -> 0.97f
        }
        val haloGuard = when (profile) {
            Profile.CLASSIC_2D -> 0.82f
            Profile.STYLIZED -> 0.85f
            Profile.CGI -> 0.89f
        }
        val chromaGuard = when (profile) {
            Profile.CLASSIC_2D -> 0.86f
            Profile.STYLIZED -> 0.89f
            Profile.CGI -> 0.92f
        }

        return Params(
            enabled = 1f,
            profile = profile,
            lineStrength = (profileLine * qualityScale).coerceIn(0f, 0.25f),
            flatProtection = flatProtection,
            haloGuard = haloGuard,
            reconstruction = (
                profileReconstruction * qualityScale * sourceScale
            ).coerceIn(0f, 0.24f),
            diagonalAssist = (
                profileDiagonal * qualityScale * sourceScale
            ).coerceIn(0f, 0.18f),
            chromaEdgeGuard = chromaGuard,
        )
    }

    private fun disabled() = Params(
        enabled = 0f,
        profile = Profile.CGI,
        lineStrength = 0f,
        flatProtection = 1f,
        haloGuard = 1f,
        reconstruction = 0f,
        diagonalAssist = 0f,
        chromaEdgeGuard = 1f,
    )
}
