package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAnimeRoutingPolicyTest {
    @Test fun `auto routes detected anime into anime engine`() {
        assertEquals(
            PictureContent.ANIME,
            PictureAnimeRoutingPolicy.resolve(PictureContent.AUTO, PictureContent.ANIME),
        )
        assertTrue(PictureAnimeRoutingPolicy.shouldRunAnime(PictureContent.AUTO, PictureContent.ANIME))
    }

    @Test fun `auto keeps detected film and animation out of anime engine`() {
        assertFalse(PictureAnimeRoutingPolicy.shouldRunAnime(PictureContent.AUTO, PictureContent.FILM))
        assertFalse(PictureAnimeRoutingPolicy.shouldRunAnime(PictureContent.AUTO, PictureContent.ANIMATION))
    }

    @Test fun `manual content choice overrides detector`() {
        assertEquals(
            PictureContent.FILM,
            PictureAnimeRoutingPolicy.resolve(PictureContent.FILM, PictureContent.ANIME),
        )
        assertFalse(PictureAnimeRoutingPolicy.shouldRunAnime(PictureContent.FILM, PictureContent.ANIME))
        assertTrue(PictureAnimeRoutingPolicy.shouldRunAnime(PictureContent.ANIME, PictureContent.FILM))
    }

    @Test fun `detector updates runtime auto route without changing saved selection`() {
        val detected = PictureContentDetector.detect("[SubsPlease] Test Episode.mkv", emptyList())
        assertEquals(PictureContent.ANIME, detected)
        assertEquals(PictureContent.ANIME, PictureAnimeRoutingPolicy.currentDetected())

        val settings = PictureSettings(
            preset = PicturePreset.NATURAL,
            content = PictureContent.AUTO,
        )
        val shader = PictureProfiles.toShaderParams(
            settings = settings,
            comparing = false,
            active = true,
        )
        assertEquals(PictureContent.ANIME, shader.content)
        assertEquals(PictureContent.AUTO, settings.content)
    }
}
