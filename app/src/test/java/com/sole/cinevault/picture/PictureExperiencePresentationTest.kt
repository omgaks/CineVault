package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureExperiencePresentationTest {
    @Test fun cards_haveOneClearActiveExperience() {
        val cards = PictureExperiencePresentation.cards(PictureExperienceFamily.ANIME, null)
        assertEquals(4, cards.size)
        assertEquals(1, cards.count { it.selected })
        assertEquals("ACTIVE EXPERIENCE", cards.first { it.selected }.eyebrow)
    }

    @Test fun autoCard_surfacesRecommendationWithoutPretendingItIsSelected() {
        val rec = PictureExperienceRecommendation(
            PictureExperienceFamily.ANIMATION,
            PictureQualityTier.BALANCED,
            "Animation · FULL HD",
        )
        val auto = PictureExperiencePresentation.cards(PictureExperienceFamily.AUTO, rec)
            .first { it.family == PictureExperienceFamily.AUTO }
        assertTrue(auto.subtitle.contains("Animation"))
        assertTrue(auto.subtitle.contains("Balanced"))
    }

    @Test fun experienceSignatures_areContentSpecific() {
        val cards = PictureExperiencePresentation.cards(PictureExperienceFamily.MOVIE, null)
        assertEquals("LINE · DETAIL · CLEAN", cards.first { it.family == PictureExperienceFamily.ANIME }.signature)
        assertEquals("COLOUR · GRADIENT · CGI", cards.first { it.family == PictureExperienceFamily.ANIMATION }.signature)
        assertEquals("NATURAL · TEXTURE · CINEMA", cards.first { it.family == PictureExperienceFamily.MOVIE }.signature)
    }

    @Test fun balancedTier_isClearlyEverydayDefaultLanguage() {
        val choices = PictureQualityPresentation.choices(PictureQualityTier.BALANCED)
        val balanced = choices.first { it.tier == PictureQualityTier.BALANCED }
        assertTrue(balanced.selected)
        assertTrue(balanced.description.contains("everyday", ignoreCase = true))
    }

    @Test fun sourceBadge_isCompactAndHonest() {
        val badge = PictureSourceProfile(
            PictureContent.ANIMATION,
            PictureSourceResolution.FULL_HD,
            PictureSourceQuality.HIGH,
            PictureSourceScalingNeed.LIGHT,
        ).toSourceBadge()
        assertEquals("Animation · 1080p", badge.primary)
        assertEquals("High quality source", badge.secondary)
        assertEquals("Light refinement", badge.scalingHint)
    }
}
