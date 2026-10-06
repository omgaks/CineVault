package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAnimeEnginePolicyTest {
    @Test fun `anime bridge is disabled for auto and film content`() {
        listOf(PictureContent.AUTO, PictureContent.FILM).forEach { content ->
            val p = PictureAnimeEnginePolicy.forState(content, 1f, 720)
            assertEquals(0f, p.enabled, 0f)
            assertEquals(0f, p.lineStrength, 0f)
            assertEquals(0f, p.reconstruction, 0f)
            assertEquals(0f, p.diagonalAssist, 0f)
        }
    }

    @Test fun `animation content is delegated to P5 animation engine`() {
        val p = PictureAnimeEnginePolicy.forState(PictureContent.ANIMATION, 1f, 720)
        val animation = PictureAnimationEnginePolicy.forState(PictureContent.ANIMATION, 1f, 720)
        assertEquals(animation.enabled, p.enabled, 0f)
        assertEquals(animation.lineStrength, p.lineStrength, 0f)
        assertEquals(animation.reconstruction, p.reconstruction, 0f)
        assertEquals(animation.diagonalAssist, p.diagonalAssist, 0f)
        assertEquals(animation.flatProtection, p.flatProtection, 0f)
        assertEquals(animation.haloGuard, p.haloGuard, 0f)
        assertEquals(animation.chromaEdgeGuard, p.chromaEdgeGuard, 0f)
    }

    @Test fun `anime engine scales from eco through max`() {
        val eco = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.62f, 720)
        val balanced = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.90f, 720)
        val max = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 1.00f, 720)
        assertTrue(eco.lineStrength < balanced.lineStrength)
        assertTrue(balanced.lineStrength < max.lineStrength)
        assertTrue(eco.reconstruction < balanced.reconstruction)
        assertTrue(balanced.reconstruction < max.reconstruction)
    }

    @Test fun `lower resolution anime receives stronger reconstruction`() {
        val sd = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.90f, 576)
        val hd = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.90f, 720)
        val fullHd = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.90f, 1080)
        val high = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, 0.90f, 1440)
        assertTrue(sd.reconstruction > hd.reconstruction)
        assertTrue(hd.reconstruction > fullHd.reconstruction)
        assertTrue(fullHd.reconstruction > high.reconstruction)
    }

    @Test fun `anime reconstruction parameters remain bounded`() {
        listOf(480, 576, 720, 1080, 1440).forEach { height ->
            listOf(0f, 0.62f, 0.90f, 1f).forEach { intensity ->
                val p = PictureAnimeEnginePolicy.forState(PictureContent.ANIME, intensity, height)
                assertTrue(p.enabled in 0f..1f)
                assertTrue(p.lineStrength in 0f..0.34f)
                assertTrue(p.reconstruction in 0f..0.30f)
                assertTrue(p.diagonalAssist in 0f..0.24f)
                assertTrue(p.flatProtection in 0f..1f)
                assertTrue(p.haloGuard in 0f..1f)
                assertTrue(p.chromaEdgeGuard in 0f..1f)
            }
        }
    }
}
