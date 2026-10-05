package com.sole.cinevault.picture

/**
 * Presentation-only model for the premium Picture experience selector.
 * It deliberately contains no Compose/Android types so visual hierarchy remains testable.
 */
data class PictureExperienceCard(
    val family: PictureExperienceFamily,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val signature: String,
    val selected: Boolean,
)

object PictureExperiencePresentation {
    fun cards(
        selected: PictureExperienceFamily,
        recommendation: PictureExperienceRecommendation?,
    ): List<PictureExperienceCard> =
        PictureExperienceFamily.entries.map { family ->
            val signature = when (family) {
                PictureExperienceFamily.AUTO -> "SOURCE AWARE"
                PictureExperienceFamily.ANIME -> "LINE · DETAIL · CLEAN"
                PictureExperienceFamily.ANIMATION -> "COLOUR · GRADIENT · CGI"
                PictureExperienceFamily.MOVIE -> "NATURAL · TEXTURE · CINEMA"
            }
            val subtitle = when (family) {
                PictureExperienceFamily.AUTO -> recommendation?.let {
                    "Recommended: ${it.family.label} · ${it.tier.name.lowercase().replaceFirstChar(Char::uppercase)}"
                } ?: family.description
                else -> family.description
            }
            PictureExperienceCard(
                family = family,
                eyebrow = if (family == selected) "ACTIVE EXPERIENCE" else signature,
                title = family.label,
                subtitle = subtitle,
                signature = signature,
                selected = family == selected,
            )
        }
}
