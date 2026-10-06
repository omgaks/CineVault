package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAnimeEnginePolicyTest {
    @Test fun `anime engine is disabled for non anime content`() {
        listOf(PictureContent.AUTO, PictureContent.ANIMATION, PictureContent.FILM).forEach { content ->
            val p = PictureAnimeEnginePolicy.forState(content, 1f)
            assertEquals(0f, p.enabled, 0f)
            assertEquals(0f, p.lineStrength, 0f)
        }
    }

    @Test fun `anime engine scales from eco through max`() {
        val eco = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.62f)
        val balanced = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.90f)
        val max = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 1.00f)
        assertTrue(eco.lineStrength < balanced.lineStrength)
        assertTrue(balanced.lineStrength < max.lineStrength)
    }

    @Test fun `anime parameters remain conservative and bounded`() {
        listOf(0f, 0.62f, 0.90f, 1f).forEach { intensity ->
            val p = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, intensity)
            assertTrue(p.enabled in 0f..1f)
            assertTrue(p.lineStrength in 0f..0.34f)
            assertTrue(p.flatProtection in 0f..1f)
            assertTrue(p.haloGuard in 0f..1f)
        }
    }
}
