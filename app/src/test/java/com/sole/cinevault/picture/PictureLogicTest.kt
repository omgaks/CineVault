package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PictureLogicTest {

    @Test
    fun detect_knownRomanisedAnimeFilm_isAnime() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect("Spirited Away (2001).mkv", listOf("Animation", "Fantasy")),
        )
    }

    @Test
    fun detect_animationGenreWithReleaseGroup_isAnime() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect("[SubsPlease] Frieren - 01 (1080p).mkv", listOf("Animation", "Drama")),
        )
    }

    @Test
    fun detect_noGenres_usesFilenameHint() {
        assertEquals(
            PictureContent.ANIME,
            PictureContentDetector.detect("[SubsPlease] Frieren - 01.mkv", emptyList()),
        )
        assertEquals(
            PictureContent.FILM,
            PictureContentDetector.detect("Random.Movie.2020.mkv", emptyList()),
        )
    }

    @Test
    fun detect_nonAnimationGenresWinOverJapaneseFilename() {
        assertEquals(
            PictureContent.FILM,
            PictureContentDetector.detect("\u5343\u3068\u5343\u5C0B.mkv", listOf("Drama")),
        )
        assertEquals(
            PictureContent.FILM,
            PictureContentDetector.detect("Parasite 2019 1080p.mkv", listOf("Thriller", "Drama")),
        )
    }

    @Test
    fun tune_animeGetsNoGrainAndExtraDeband() {
        assertEquals(0.15f, PictureProfiles.tune(PicturePreset.CINEMA, PictureContent.FILM).grain, 0.0001f)
        assertEquals(0f, PictureProfiles.tune(PicturePreset.CINEMA, PictureContent.ANIME).grain, 0.0001f)
        assertEquals(0.65f, PictureProfiles.tune(PicturePreset.NATURAL, PictureContent.ANIME).deband, 0.0001f)
        assertEquals(0.425f, PictureProfiles.tune(PicturePreset.NATURAL, PictureContent.ANIME).sharpen, 0.0001f)
    }

    @Test
    fun resolveContent_autoUsesDetected() {
        assertEquals(PictureContent.ANIME, PictureProfiles.resolveContent(PictureContent.AUTO, PictureContent.ANIME))
        assertEquals(PictureContent.FILM, PictureProfiles.resolveContent(PictureContent.FILM, PictureContent.ANIME))
    }

    @Test
    fun shaderParams_compareAndInactiveAreZero() {
        val settings = PictureSettings(preset = PicturePreset.NATURAL, intensity = 0.8f)
        assertEquals(0.8f, PictureProfiles.toShaderParams(settings, comparing = false, active = true).amount, 0.0001f)
        assertEquals(0f, PictureProfiles.toShaderParams(settings, comparing = true, active = true).amount, 0.0001f)
        assertSame(
            PictureShaderParams.OFF,
            PictureProfiles.toShaderParams(settings, comparing = false, active = false),
        )
        assertSame(
            PictureShaderParams.OFF,
            PictureProfiles.toShaderParams(PictureSettings(), comparing = false, active = true),
        )
    }

    @Test
    fun codec_roundTripsAndRejectsGarbage() {
        val settings = PictureSettings(
            preset = PicturePreset.CINEMA,
            content = PictureContent.ANIME,
            intensity = 0.5f,
            sharpen = 0.25f,
            deband = 0.75f,
            colour = 0.125f,
            grain = 0f,
        )
        assertEquals(settings, PictureSettingsCodec.decode(PictureSettingsCodec.encode(settings)))
        assertNull(PictureSettingsCodec.decode(null))
        assertNull(PictureSettingsCodec.decode("x"))
        assertNull(PictureSettingsCodec.decode("BOGUS,AUTO,0.5,0.5,0.5,0.5,0.5"))
        assertEquals(1f, PictureSettingsCodec.decode("NATURAL,AUTO,5.0,0.5,0.5,0.5,0.5")!!.intensity, 0.0001f)
    }

    @Test
    fun splitView_onlyWhileNotComparing() {
        val settings = PictureSettings(preset = PicturePreset.NATURAL)
        assertEquals(0.5f, PictureProfiles.toShaderParams(settings, false, true, splitView = true).split, 0.0001f)
        assertEquals(0f, PictureProfiles.toShaderParams(settings, true, true, splitView = true).split, 0.0001f)
        assertEquals(0f, PictureProfiles.toShaderParams(settings, false, true, splitView = false).split, 0.0001f)
    }

    @Test
    fun resetFineTune_restoresPresetValues() {
        val moved = PictureProfiles.withPreset(PictureSettings(), PicturePreset.VIVID, PictureContent.FILM)
            .copy(preset = PicturePreset.CUSTOM, sharpen = 0.01f, deband = 0.99f, colour = 0.0f, grain = 0.7f)
        val reset = PictureProfiles.resetFineTune(moved, PictureContent.FILM, PicturePreset.VIVID)
        assertEquals(PicturePreset.VIVID, reset.preset)
        assertEquals(0.50f, reset.sharpen, 0.0001f)
        assertEquals(0.45f, reset.colour, 0.0001f)
        assertEquals(0f, reset.grain, 0.0001f)
    }

    @Test
    fun resetFineTune_keepsOffOff() {
        val reset = PictureProfiles.resetFineTune(PictureSettings(), PictureContent.FILM, PicturePreset.NATURAL)
        assertEquals(PicturePreset.OFF, reset.preset)
    }

    @Test
    fun resetAll_keepsOnOffAndRestoresDefaults() {
        val custom = PictureSettings(
            preset = PicturePreset.SHARP, content = PictureContent.ANIME, intensity = 0.2f,
            sharpen = 0.9f, deband = 0.1f, colour = 0.9f, grain = 0.9f,
        )
        val reset = PictureProfiles.resetAll(custom, PictureContent.FILM)
        assertEquals(PicturePreset.NATURAL, reset.preset)
        assertEquals(PictureContent.AUTO, reset.content)
        assertEquals(0.9f, reset.intensity, 0.0001f)
        assertEquals(0f, reset.grain, 0.0001f)
        assertEquals(PicturePreset.OFF, PictureProfiles.resetAll(PictureSettings(), PictureContent.FILM).preset)
    }

    @Test
    fun splitPosition_isClampedAndUsed() {
        val settings = PictureSettings(preset = PicturePreset.NATURAL)
        assertEquals(0.3f, PictureProfiles.toShaderParams(settings, false, true, true, 0.3f).split, 0.0001f)
        assertEquals(0.95f, PictureProfiles.toShaderParams(settings, false, true, true, 2f).split, 0.0001f)
        assertEquals(0.05f, PictureProfiles.toShaderParams(settings, false, true, true, -1f).split, 0.0001f)
    }
}
