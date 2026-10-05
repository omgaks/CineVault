package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureSourceIntelligenceTest {
    @Test fun animeDetection_flowsIntoAnimeExperience() {
        val p = PictureSourceIntelligence.analyze(
            PictureSourceSignals("[SubsPlease] Frieren 01.mkv", listOf("Animation"), 1920, 1080, 5_000_000)
        )
        assertEquals(PictureContent.ANIME, p.content)
        assertEquals(PictureSourceResolution.FULL_HD, p.resolution)
        assertEquals(PictureExperienceFamily.ANIME, PictureExperienceRecommender.recommend(p).family)
    }

    @Test fun animationWithoutAnimeHints_becomesAnimation() {
        val p = PictureSourceIntelligence.analyze(
            PictureSourceSignals("The Incredibles.mkv", listOf("Animation", "Family"), 1920, 1080, 8_000_000)
        )
        assertEquals(PictureContent.ANIMATION, p.content)
        assertEquals(PictureExperienceFamily.ANIMATION, PictureExperienceRecommender.recommend(p).family)
    }

    @Test fun liveActionGenre_winsOverJapaneseFilename() {
        val p = PictureSourceIntelligence.analyze(
            PictureSourceSignals("東京物語.mkv", listOf("Drama"), 1920, 1080, 7_000_000)
        )
        assertEquals(PictureContent.FILM, p.content)
        assertEquals(PictureExperienceFamily.MOVIE, PictureExperienceRecommender.recommend(p).family)
    }

    @Test fun sourceResolution_drivesScalingNeed() {
        assertEquals(PictureSourceScalingNeed.STRONG,
            PictureSourceIntelligence.classifyScalingNeed(PictureSourceResolution.SD))
        assertEquals(PictureSourceScalingNeed.MODERATE,
            PictureSourceIntelligence.classifyScalingNeed(PictureSourceResolution.HD))
        assertEquals(PictureSourceScalingNeed.NONE,
            PictureSourceIntelligence.classifyScalingNeed(PictureSourceResolution.UHD))
    }

    @Test fun bitrateQuality_isResolutionAware() {
        assertEquals(PictureSourceQuality.LOW,
            PictureSourceIntelligence.classifyQuality(PictureSourceResolution.FULL_HD, 2_000_000))
        assertEquals(PictureSourceQuality.HIGH,
            PictureSourceIntelligence.classifyQuality(PictureSourceResolution.FULL_HD, 10_000_000))
    }

    @Test fun uhdRecommendation_remainsConservative() {
        val p = PictureSourceProfile(
            PictureContent.FILM, PictureSourceResolution.UHD,
            PictureSourceQuality.HIGH, PictureSourceScalingNeed.NONE
        )
        assertEquals(PictureQualityTier.ECO,
            PictureExperienceRecommender.recommend(p, PictureQualityTier.MAX).tier)
    }
}
