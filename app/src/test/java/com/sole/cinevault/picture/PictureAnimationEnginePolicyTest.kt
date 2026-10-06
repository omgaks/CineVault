package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAnimationEnginePolicyTest {

    @Test
    fun `animation engine is isolated from anime film and auto`() {
        listOf(
            PictureContent.AUTO,
            PictureContent.ANIME,
            PictureContent.FILM,
        ).forEach { content ->
            val p = PictureAnimationEnginePolicy.forState(content, 1f, 720)
            assertEquals(0f, p.enabled, 0f)
            assertEquals(0f, p.lineStrength, 0f)
            assertEquals(0f, p.reconstruction, 0f)
            assertEquals(0f, p.diagonalAssist, 0f)
        }
    }

    @Test
    fun `animation quality scales eco balanced max`() {
        val eco = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION, 0.62f, 720
        )
        val balanced = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION, 0.90f, 720
        )
        val max = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION, 1.00f, 720
        )

        assertTrue(eco.lineStrength < balanced.lineStrength)
        assertTrue(balanced.lineStrength < max.lineStrength)
        assertTrue(eco.reconstruction < balanced.reconstruction)
        assertTrue(balanced.reconstruction < max.reconstruction)
    }

    @Test
    fun `animation profiles become more conservative for modern high resolution sources`() {
        val classic = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION, 0.90f, 576
        )
        val stylized = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION, 0.90f, 720
        )
        val cgi = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION, 0.90f, 1080
        )

        assertEquals(PictureAnimationEnginePolicy.Profile.CLASSIC_2D, classic.profile)
        assertEquals(PictureAnimationEnginePolicy.Profile.STYLIZED, stylized.profile)
        assertEquals(PictureAnimationEnginePolicy.Profile.CGI, cgi.profile)

        assertTrue(classic.lineStrength > stylized.lineStrength)
        assertTrue(stylized.lineStrength > cgi.lineStrength)
        assertTrue(classic.reconstruction > stylized.reconstruction)
        assertTrue(stylized.reconstruction > cgi.reconstruction)
        assertTrue(classic.flatProtection < cgi.flatProtection)
        assertTrue(classic.haloGuard < cgi.haloGuard)
    }

    @Test
    fun `p5 animation is routed through existing single pass execution policy`() {
        val p = PictureAnimeEnginePolicy.forState(
            PictureContent.ANIMATION,
            0.90f,
            720,
        )
        val expected = PictureAnimationEnginePolicy.forState(
            PictureContent.ANIMATION,
            0.90f,
            720,
        )

        assertEquals(1f, p.enabled, 0f)
        assertEquals(expected.lineStrength, p.lineStrength, 0f)
        assertEquals(expected.flatProtection, p.flatProtection, 0f)
        assertEquals(expected.haloGuard, p.haloGuard, 0f)
        assertEquals(expected.reconstruction, p.reconstruction, 0f)
        assertEquals(expected.diagonalAssist, p.diagonalAssist, 0f)
        assertEquals(expected.chromaEdgeGuard, p.chromaEdgeGuard, 0f)
    }

    @Test
    fun `p4 anime strengths remain stronger than p5 animation`() {
        val anime = PictureAnimeEnginePolicy.forState(
            PictureContent.ANIME, 0.90f, 720
        )
        val animation = PictureAnimeEnginePolicy.forState(
            PictureContent.ANIMATION, 0.90f, 720
        )

        assertTrue(anime.lineStrength > animation.lineStrength)
        assertTrue(anime.reconstruction > animation.reconstruction)
        assertTrue(anime.diagonalAssist > animation.diagonalAssist)
    }

    @Test
    fun `p5 parameters stay inside conservative bounds`() {
        listOf(480, 576, 720, 900, 1080, 1440).forEach { height ->
            listOf(0f, 0.62f, 0.90f, 1f).forEach { intensity ->
                val p = PictureAnimationEnginePolicy.forState(
                    PictureContent.ANIMATION,
                    intensity,
                    height,
                )
                assertTrue(p.enabled in 0f..1f)
                assertTrue(p.lineStrength in 0f..0.25f)
                assertTrue(p.reconstruction in 0f..0.24f)
                assertTrue(p.diagonalAssist in 0f..0.18f)
                assertTrue(p.flatProtection in 0f..1f)
                assertTrue(p.haloGuard in 0f..1f)
                assertTrue(p.chromaEdgeGuard in 0f..1f)
            }
        }
    }
}
