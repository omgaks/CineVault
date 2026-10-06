package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureMovieEnginePolicyTest {

    @Test
    fun `movie engine activates only for Film`() {
        val film = PictureMovieEnginePolicy.forState(PictureContent.FILM, 0.9f, 1080)
        assertEquals(1f, film.enabled, 0f)

        listOf(PictureContent.AUTO, PictureContent.ANIME, PictureContent.ANIMATION).forEach {
            assertEquals(0f, PictureMovieEnginePolicy.forState(it, 0.9f, 1080).enabled, 0f)
        }
    }

    @Test
    fun `lower resolution receives more recovery than UHD`() {
        val sd = PictureMovieEnginePolicy.forState(PictureContent.FILM, 1f, 576)
        val uhd = PictureMovieEnginePolicy.forState(PictureContent.FILM, 1f, 2160)
        assertTrue(sd.detailRecovery > uhd.detailRecovery)
    }

    @Test
    fun `UHD receives stronger texture grain halo and chroma protection`() {
        val sd = PictureMovieEnginePolicy.forState(PictureContent.FILM, 1f, 576)
        val uhd = PictureMovieEnginePolicy.forState(PictureContent.FILM, 1f, 2160)

        assertTrue(uhd.textureProtection > sd.textureProtection)
        assertTrue(uhd.grainProtection > sd.grainProtection)
        assertTrue(uhd.haloGuard > sd.haloGuard)
        assertTrue(uhd.chromaGuard > sd.chromaGuard)
        assertTrue(uhd.sharpenCeiling < sd.sharpenCeiling)
    }

    @Test
    fun `Max is stronger than Balanced and Eco without changing safety guards`() {
        val eco = PictureMovieEnginePolicy.forState(PictureContent.FILM, 0.6f, 720)
        val balanced = PictureMovieEnginePolicy.forState(PictureContent.FILM, 0.9f, 720)
        val max = PictureMovieEnginePolicy.forState(PictureContent.FILM, 1f, 720)

        assertTrue(eco.detailRecovery < balanced.detailRecovery)
        assertTrue(balanced.detailRecovery < max.detailRecovery)
        assertEquals(eco.skinProtection, max.skinProtection, 0f)
        assertEquals(eco.haloGuard, max.haloGuard, 0f)
        assertEquals(eco.chromaGuard, max.chromaGuard, 0f)
    }

    @Test
    fun `movie engine keeps face and grain protection conservative`() {
        val p = PictureMovieEnginePolicy.forState(PictureContent.FILM, 1f, 1080)
        assertTrue(p.skinProtection >= 0.90f)
        assertTrue(p.grainProtection >= 0.85f)
        assertTrue(p.sharpenCeiling <= 0.26f)
    }

    @Test
    fun `movie policy never performs semantic classification`() {
        val low = PictureMovieEnginePolicy.forState(PictureContent.FILM, 0.9f, 480)
        val high = PictureMovieEnginePolicy.forState(PictureContent.FILM, 0.9f, 2160)
        assertEquals(1f, low.enabled, 0f)
        assertEquals(1f, high.enabled, 0f)
    }
}
