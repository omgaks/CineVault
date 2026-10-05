package com.sole.cinevault.picture

data class PictureSourceBadge(
    val primary: String,
    val secondary: String?,
    val scalingHint: String?,
)

fun PictureSourceProfile.toSourceBadge(): PictureSourceBadge {
    val primary = buildString {
        append(
            when (content) {
                PictureContent.ANIME -> "Anime"
                PictureContent.ANIMATION -> "Animation"
                PictureContent.FILM -> "Movie"
                PictureContent.AUTO -> "Auto"
            }
        )
        if (resolution != PictureSourceResolution.UNKNOWN) {
            append(" · ")
            append(
                when (resolution) {
                    PictureSourceResolution.FULL_HD -> "1080p"
                    PictureSourceResolution.HD -> "HD"
                    PictureSourceResolution.SD -> "SD"
                    PictureSourceResolution.QHD -> "QHD"
                    PictureSourceResolution.UHD -> "4K"
                    PictureSourceResolution.UNKNOWN -> ""
                }
            )
        }
    }
    val secondary = when (quality) {
        PictureSourceQuality.LOW -> "Low bitrate source"
        PictureSourceQuality.MEDIUM -> "Standard source"
        PictureSourceQuality.HIGH -> "High quality source"
        PictureSourceQuality.UNKNOWN -> null
    }
    val scaling = when (scalingNeed) {
        PictureSourceScalingNeed.STRONG -> "Strong reconstruction recommended"
        PictureSourceScalingNeed.MODERATE -> "Reconstruction recommended"
        PictureSourceScalingNeed.LIGHT -> "Light refinement"
        PictureSourceScalingNeed.NONE -> "Native detail"
        PictureSourceScalingNeed.UNKNOWN -> null
    }
    return PictureSourceBadge(primary, secondary, scaling)
}
