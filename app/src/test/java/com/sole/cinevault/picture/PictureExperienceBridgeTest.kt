package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureExperienceBridgeTest {

    @Test
    fun selectedFamily_mapsExistingContentWithoutChangingMeaning() {
        assertEquals(
            PictureExperienceFamily.AUTO,
            PictureExperienceBridge.selectedFamily(PictureSettings(content = PictureContent.AUTO)),
        )
        assertEquals(
            PictureExperienceFamily.ANIME,
            PictureExperienceBridge.selectedFamily(PictureSettings(content = PictureContent.ANIME)),
        )
        assertEquals(
            PictureExperienceFamily.ANIMATION,
            PictureExperienceBridge.selectedFamily(PictureSettings(content = PictureContent.ANIMATION)),
        )
        assertEquals(
            PictureExperienceFamily.MOVIE,
            PictureExperienceBridge.selectedFamily(PictureSettings(content = PictureContent.FILM)),
        )
    }

    @Test
    fun familySelection_reusesExistingProfileTuning() {
        val before = PictureSettings(
            preset = PicturePreset.CINEMA,
            content = PictureContent.AUTO,
            intensity = 0.90f,
        )

        val after = PictureExperienceBridge.applyFamily(
            settings = before,
            family = PictureExperienceFamily.ANIME,
            detected = PictureContent.FILM,
        )

        val expected = PictureProfiles.tune(PicturePreset.CINEMA, PictureContent.ANIME)
        assertEquals(PictureContent.ANIME, after.content)
        assertEquals(PicturePreset.CINEMA, after.preset)
        assertEquals(expected.sharpen, after.sharpen, 0.0001f)
        assertEquals(expected.deband, after.deband, 0.0001f)
        assertEquals(expected.colour, after.colour, 0.0001f)
        assertEquals(expected.grain, after.grain, 0.0001f)
    }

    @Test
    fun selectingExperienceWhileOff_usesSafeNaturalBase() {
        val after = PictureExperienceBridge.applyFamily(
            settings = PictureSettings(preset = PicturePreset.OFF),
            family = PictureExperienceFamily.MOVIE,
            detected = PictureContent.FILM,
        )

        assertEquals(PicturePreset.NATURAL, after.preset)
        assertEquals(PictureContent.FILM, after.content)
    }

    @Test
    fun qualityTiers_mapToStableExistingIntensityControl() {
        assertEquals(0.62f, PictureExperienceBridge.intensityFor(PictureQualityTier.ECO), 0.0001f)
        assertEquals(0.90f, PictureExperienceBridge.intensityFor(PictureQualityTier.BALANCED), 0.0001f)
        assertEquals(1.00f, PictureExperienceBridge.intensityFor(PictureQualityTier.MAX), 0.0001f)

        assertEquals(
            PictureQualityTier.ECO,
            PictureExperienceBridge.qualityFor(PictureSettings(intensity = 0.62f)),
        )
        assertEquals(
            PictureQualityTier.BALANCED,
            PictureExperienceBridge.qualityFor(PictureSettings(intensity = 0.90f)),
        )
        assertEquals(
            PictureQualityTier.MAX,
            PictureExperienceBridge.qualityFor(PictureSettings(intensity = 1.00f)),
        )
    }

    @Test
    fun applyingQuality_changesOnlyIntensity() {
        val before = PictureSettings(
            preset = PicturePreset.CINEMA,
            content = PictureContent.ANIMATION,
            intensity = 0.42f,
            sharpen = 0.31f,
            deband = 0.72f,
            colour = 0.28f,
            grain = 0.08f,
        )
        val after = PictureExperienceBridge.applyQuality(before, PictureQualityTier.MAX)

        assertEquals(1.00f, after.intensity, 0.0001f)
        assertEquals(before.copy(intensity = 1.00f), after)
        assertTrue(after.preset == before.preset && after.content == before.content)
    }
}
