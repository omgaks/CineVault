package com.sole.cinevault.picture

/**
 * P2 Slice 4 bridge between the premium Experience/Quality UX and the existing
 * proven PictureSettings/rendering pipeline.
 *
 * This deliberately contains no Compose or Android types so the mapping stays
 * deterministic and can be locked by plain JVM tests before the panel is wired.
 */
object PictureExperienceBridge {

    fun selectedFamily(settings: PictureSettings): PictureExperienceFamily =
        when (settings.content) {
            PictureContent.AUTO -> PictureExperienceFamily.AUTO
            PictureContent.ANIME -> PictureExperienceFamily.ANIME
            PictureContent.ANIMATION -> PictureExperienceFamily.ANIMATION
            PictureContent.FILM -> PictureExperienceFamily.MOVIE
        }

    fun contentFor(family: PictureExperienceFamily): PictureContent =
        when (family) {
            PictureExperienceFamily.AUTO -> PictureContent.AUTO
            PictureExperienceFamily.ANIME -> PictureContent.ANIME
            PictureExperienceFamily.ANIMATION -> PictureContent.ANIMATION
            PictureExperienceFamily.MOVIE -> PictureContent.FILM
        }

    /**
     * Keep the current renderer honest: premium families select content policy,
     * while the existing preset engine continues to own the actual shader tune.
     */
    fun applyFamily(
        settings: PictureSettings,
        family: PictureExperienceFamily,
        detected: PictureContent,
    ): PictureSettings {
        val content = contentFor(family)
        val resolved = PictureProfiles.resolveContent(content, detected)
        val preset = usablePreset(settings.preset)
        return PictureProfiles.withPreset(
            settings = settings.copy(content = content),
            preset = preset,
            resolvedContent = resolved,
        )
    }

    /**
     * P2 quality is expressed through the existing intensity control for now.
     * No unsupported decoder/GPU claim is introduced by the UX.
     */
    fun applyQuality(
        settings: PictureSettings,
        tier: PictureQualityTier,
    ): PictureSettings = settings.copy(intensity = intensityFor(tier))

    fun qualityFor(settings: PictureSettings): PictureQualityTier =
        when {
            settings.intensity < 0.72f -> PictureQualityTier.ECO
            settings.intensity >= 0.96f -> PictureQualityTier.MAX
            else -> PictureQualityTier.BALANCED
        }

    fun intensityFor(tier: PictureQualityTier): Float =
        when (tier) {
            PictureQualityTier.ECO -> 0.62f
            PictureQualityTier.BALANCED -> 0.90f
            PictureQualityTier.MAX -> 1.00f
        }

    private fun usablePreset(preset: PicturePreset): PicturePreset =
        when (preset) {
            PicturePreset.OFF -> PicturePreset.NATURAL
            PicturePreset.CUSTOM -> PicturePreset.NATURAL
            else -> preset
        }
}
