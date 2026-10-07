package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureMovieEnginePolicyTest {
    @Test fun `film only`() {
        assertEquals(1f,PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,1080).enabled,0f)
        listOf(PictureContent.AUTO,PictureContent.ANIME,PictureContent.ANIMATION).forEach {
            assertEquals(0f,PictureMovieEnginePolicy.forState(it,.9f,1080).enabled,0f)
        }
    }

    @Test fun `lower resolution receives more recovery`() {
        val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,576)
        val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,2160)
        assertTrue(sd.detailRecovery>uhd.detailRecovery)
    }

    @Test fun `UHD strengthens preservation and narrows recovery band`() {
        val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,576)
        val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,2160)
        assertTrue(uhd.textureProtection>sd.textureProtection)
        assertTrue(uhd.grainProtection>sd.grainProtection)
        assertTrue(uhd.haloGuard>sd.haloGuard)
        assertTrue(uhd.chromaGuard>sd.chromaGuard)
        assertTrue(uhd.sharpenCeiling<sd.sharpenCeiling)
        assertTrue(uhd.structureFloor>sd.structureFloor)
        assertTrue(uhd.textureCeiling<sd.textureCeiling)
    }

    @Test fun `quality changes recovery not protection gates`() {
        val eco=PictureMovieEnginePolicy.forState(PictureContent.FILM,.6f,720)
        val balanced=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,720)
        val max=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,720)
        assertTrue(eco.detailRecovery<balanced.detailRecovery)
        assertTrue(balanced.detailRecovery<max.detailRecovery)
        assertEquals(eco.structureFloor,max.structureFloor,0f)
        assertEquals(eco.textureCeiling,max.textureCeiling,0f)
        assertEquals(eco.skinProtection,max.skinProtection,0f)
    }

    @Test fun `face recovery remains conservative`() {
        val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,576)
        val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,2160)
        assertTrue(sd.skinProtection>=.90f)
        assertTrue(sd.faceRecoveryScale<=.22f)
        assertTrue(uhd.faceRecoveryScale<=sd.faceRecoveryScale)
        assertTrue(uhd.faceRecoveryScale>=.16f)
    }

    @Test fun `film sharpen ceiling preserves meaningful user control`() {
        val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,576)
        val hd=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,1080)
        val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,2160)

        // Natural (0.50) must pass untouched, while Sharp/Fine Tune retain a clearly
        // stronger range instead of being flattened to the old 0.18-0.26 ceiling.
        assertTrue(sd.sharpenCeiling>=.70f)
        assertTrue(hd.sharpenCeiling>=.70f)
        assertTrue(uhd.sharpenCeiling>=.70f)
        assertTrue(sd.sharpenCeiling>uhd.sharpenCeiling)
    }

    @Test fun `disabled path cannot leak movie recovery`() {
        listOf(PictureContent.AUTO,PictureContent.ANIME,PictureContent.ANIMATION).forEach {
            val p=PictureMovieEnginePolicy.forState(it,1f,576)
            assertEquals(0f,p.enabled,0f)
            assertEquals(0f,p.detailRecovery,0f)
            assertEquals(0f,p.faceRecoveryScale,0f)
            assertEquals(0f,p.sharpenCeiling,0f)
        }
    }

    @Test fun `all film parameters remain inside closure bounds`() {
        listOf(1,240,480,576,720,1080,1440,2160,4320).forEach { height ->
            listOf(-1f,0f,.62f,.90f,1f,2f).forEach { intensity ->
                val p=PictureMovieEnginePolicy.forState(PictureContent.FILM,intensity,height)
                assertEquals(1f,p.enabled,0f)
                assertTrue(p.detailRecovery in 0f..0.22f)
                assertTrue(p.textureProtection in .84f..1f)
                assertTrue(p.grainProtection in .86f..1f)
                assertTrue(p.skinProtection in .90f..1f)
                assertTrue(p.haloGuard in .86f..1f)
                assertTrue(p.chromaGuard in .90f..1f)
                assertTrue(p.sharpenCeiling in .70f..0.82f)
                assertTrue(p.structureFloor in .010f..0.016f)
                assertTrue(p.textureCeiling in .046f..0.060f)
                assertTrue(p.faceRecoveryScale in .16f..0.22f)
            }
        }
    }

    @Test fun `invalid source height is sanitized without changing film routing`() {
        val invalid=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,-100)
        val minimum=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,1)
        assertEquals(minimum,invalid)
    }

    @Test fun `out of range intensity clamps to supported quality envelope`() {
        val low=PictureMovieEnginePolicy.forState(PictureContent.FILM,-4f,720)
        val zero=PictureMovieEnginePolicy.forState(PictureContent.FILM,0f,720)
        val high=PictureMovieEnginePolicy.forState(PictureContent.FILM,4f,720)
        val one=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,720)
        assertEquals(zero,low)
        assertEquals(one,high)
    }
}
