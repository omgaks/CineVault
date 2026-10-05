package com.sole.cinevault.picture

enum class PictureSourceResolution { SD, HD, FULL_HD, QHD, UHD, UNKNOWN }
enum class PictureSourceQuality { LOW, MEDIUM, HIGH, UNKNOWN }
enum class PictureSourceScalingNeed { NONE, LIGHT, MODERATE, STRONG, UNKNOWN }

data class PictureSourceSignals(
    val fileName: String,
    val genres: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val bitrateBitsPerSecond: Long? = null,
)

data class PictureSourceProfile(
    val content: PictureContent,
    val resolution: PictureSourceResolution,
    val quality: PictureSourceQuality,
    val scalingNeed: PictureSourceScalingNeed,
)

/**
 * P2 source intelligence foundation.
 * Uses only facts CineVault can know cheaply and deterministically today.
 * Artifact/noise/banding analysis remains for later measured P2/P3 work.
 */
object PictureSourceIntelligence {

    fun analyze(signals: PictureSourceSignals): PictureSourceProfile {
        val content = PictureContentDetector.detect(signals.fileName, signals.genres)
        val resolution = classifyResolution(signals.width, signals.height)
        return PictureSourceProfile(
            content = content,
            resolution = resolution,
            quality = classifyQuality(resolution, signals.bitrateBitsPerSecond),
            scalingNeed = classifyScalingNeed(resolution),
        )
    }

    fun classifyResolution(width: Int, height: Int): PictureSourceResolution {
        if (width <= 0 || height <= 0) return PictureSourceResolution.UNKNOWN
        val longEdge = maxOf(width, height)
        val shortEdge = minOf(width, height)
        return when {
            longEdge >= 3200 || shortEdge >= 1800 -> PictureSourceResolution.UHD
            longEdge > 1920 || shortEdge > 1080 -> PictureSourceResolution.QHD
            longEdge >= 1600 || shortEdge >= 900 -> PictureSourceResolution.FULL_HD
            longEdge >= 960 || shortEdge >= 540 -> PictureSourceResolution.HD
            else -> PictureSourceResolution.SD
        }
    }

    fun classifyQuality(
        resolution: PictureSourceResolution,
        bitrateBitsPerSecond: Long?,
    ): PictureSourceQuality {
        if (bitrateBitsPerSecond == null || bitrateBitsPerSecond <= 0) {
            return PictureSourceQuality.UNKNOWN
        }
        val mbps = bitrateBitsPerSecond / 1_000_000.0
        val lowThreshold = when (resolution) {
            PictureSourceResolution.SD -> 0.8
            PictureSourceResolution.HD -> 1.8
            PictureSourceResolution.FULL_HD -> 3.5
            PictureSourceResolution.QHD -> 6.0
            PictureSourceResolution.UHD -> 10.0
            PictureSourceResolution.UNKNOWN -> return PictureSourceQuality.UNKNOWN
        }
        val highThreshold = lowThreshold * 2.5
        return when {
            mbps < lowThreshold -> PictureSourceQuality.LOW
            mbps >= highThreshold -> PictureSourceQuality.HIGH
            else -> PictureSourceQuality.MEDIUM
        }
    }

    fun classifyScalingNeed(resolution: PictureSourceResolution): PictureSourceScalingNeed =
        when (resolution) {
            PictureSourceResolution.SD -> PictureSourceScalingNeed.STRONG
            PictureSourceResolution.HD -> PictureSourceScalingNeed.MODERATE
            PictureSourceResolution.FULL_HD -> PictureSourceScalingNeed.LIGHT
            PictureSourceResolution.QHD, PictureSourceResolution.UHD -> PictureSourceScalingNeed.NONE
            PictureSourceResolution.UNKNOWN -> PictureSourceScalingNeed.UNKNOWN
        }
}
