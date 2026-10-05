package com.sole.cinevault.picture

data class PictureQualityChoice(
    val tier: PictureQualityTier,
    val label: String,
    val description: String,
    val selected: Boolean,
)

object PictureQualityPresentation {
    fun choices(selected: PictureQualityTier): List<PictureQualityChoice> =
        PictureQualityTier.entries.map { tier ->
            val (label, description) = when (tier) {
                PictureQualityTier.ECO -> "Eco" to "Lightest processing · maximum headroom"
                PictureQualityTier.BALANCED -> "Balanced" to "Best everyday quality / performance"
                PictureQualityTier.MAX -> "Max" to "Highest enhancement · hardware permitting"
            }
            PictureQualityChoice(tier, label, description, tier == selected)
        }
}
