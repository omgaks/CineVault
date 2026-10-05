package com.sole.cinevault.picture

enum class PictureExperienceFamily(val label: String, val description: String) {
    AUTO("Auto", "Let CineVault choose from the source"),
    ANIME("Anime", "Clean lines, controlled detail, restrained texture"),
    ANIMATION("Animation", "Smooth gradients, vivid CGI detail, clean edges"),
    MOVIE("Movie", "Natural detail, cinematic texture, restrained sharpening"),
}

data class PictureExperienceRecommendation(
    val family: PictureExperienceFamily,
    val tier: PictureQualityTier,
    val reason: String,
)

object PictureExperienceRecommender {
    fun recommend(
        source: PictureSourceProfile,
        preferredTier: PictureQualityTier = PictureQualityTier.BALANCED,
    ): PictureExperienceRecommendation {
        val family = when (source.content) {
            PictureContent.ANIME -> PictureExperienceFamily.ANIME
            PictureContent.ANIMATION -> PictureExperienceFamily.ANIMATION
            PictureContent.FILM -> PictureExperienceFamily.MOVIE
            PictureContent.AUTO -> PictureExperienceFamily.AUTO
        }
        val tier = if (source.resolution == PictureSourceResolution.UHD) {
            PictureQualityTier.ECO
        } else {
            preferredTier
        }
        val reason = buildString {
            append(family.label)
            if (source.resolution != PictureSourceResolution.UNKNOWN) {
                append(" · ")
                append(source.resolution.name.replace('_', ' '))
            }
            if (source.quality != PictureSourceQuality.UNKNOWN) {
                append(" · ")
                append(source.quality.name.lowercase())
                append(" source")
            }
        }
        return PictureExperienceRecommendation(family, tier, reason)
    }
}
