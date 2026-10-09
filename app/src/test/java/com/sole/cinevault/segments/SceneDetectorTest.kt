package com.sole.cinevault.segments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDetectorTest {

    private val minute = 60_000L
    private val duration = 150 * minute // a 2h30 film

    // ---- audio helpers ----------------------------------------------------------------

    /** Hops of 500 ms: [loud] music, a silent gap, then sound again until the end. */
    private fun envelope(
        startMs: Long,
        musicSec: Int,
        silenceSec: Int,
        sceneSec: Int,
        musicLevel: Float = 0.10f,
        sceneLevel: Float = 0.08f
    ): AudioEnvelope {
        val values = ArrayList<Float>()
        repeat(musicSec * 2) { values.add(musicLevel * (0.9f + 0.2f * ((it % 5) / 4f))) }
        repeat(silenceSec * 2) { values.add(0.0005f) }
        repeat(sceneSec * 2) { values.add(sceneLevel * (0.8f + 0.4f * ((it % 7) / 6f))) }
        return AudioEnvelope(startMs, 500, values.toFloatArray())
    }

    // ---- credits start ----------------------------------------------------------------

    @Test fun creditsStartIsClamped() {
        assertEquals(duration - 10 * minute, SceneDetector.estimatedCreditsStartMs(duration))
        assertEquals(60 * minute - 252_000L, SceneDetector.estimatedCreditsStartMs(60 * minute)) // 7% = 4.2 min
        assertEquals(30 * minute - 4 * minute, SceneDetector.estimatedCreditsStartMs(30 * minute)) // 7% < 4 min, clamped up
        assertNull(SceneDetector.estimatedCreditsStartMs(0))
    }

    // ---- audio ------------------------------------------------------------------------

    @Test fun audioFindsSilenceThenSound() {
        // Window starts at the credits (140:00): 8 min music, 5 s silence, 90 s scene = ends at 150:00-ish.
        val start = duration - 10 * minute
        val env = envelope(start, musicSec = 8 * 60 + 25, silenceSec = 5, sceneSec = 90)
        val onset = start + (8 * 60 + 25 + 5) * 1000L
        val found = SceneDetector.fromAudio(env, creditsStartMs = start, durationMs = onset + 90_000L, preferLatest = true)
        assertNotNull(found)
        assertTrue(Math.abs(found!!.startMs - onset) < 2_000L)
        assertTrue(found.confidence in 20..70)
    }

    @Test fun audioIgnoresContinuousMusic() {
        val start = duration - 10 * minute
        val env = envelope(start, musicSec = 600, silenceSec = 0, sceneSec = 0)
        assertNull(SceneDetector.fromAudio(env, start, duration, preferLatest = true))
    }

    @Test fun audioIgnoresSilenceAtTheEnd() {
        // Music, then silence to the very end: nothing comes back, so there is no scene.
        val start = duration - 10 * minute
        val env = envelope(start, musicSec = 560, silenceSec = 40, sceneSec = 0)
        assertNull(SceneDetector.fromAudio(env, start, duration, preferLatest = true))
    }

    @Test fun audioIgnoresBriefDip() {
        val start = duration - 10 * minute
        val env = envelope(start, musicSec = 300, silenceSec = 1, sceneSec = 299)
        assertNull(SceneDetector.fromAudio(env, start, duration, preferLatest = true))
    }

    @Test fun envelopeOfComputesRms() {
        val samples = FloatArray(4000) { if (it < 2000) 0.5f else 0f } // 4 kHz, 1 s: loud then silent
        val env = SceneDetector.envelopeOf(samples, 4000, startMs = 1000L)
        assertEquals(2, env.rms.size)
        assertEquals(0.5f, env.rms[0], 0.001f)
        assertEquals(0f, env.rms[1], 0.001f)
        assertEquals(1000L, env.startMs)
    }

    // ---- subtitles --------------------------------------------------------------------

    /** Dialogue every 4 s up to [filmEndSec], then a lone line at [sceneSec]. */
    private fun lines(filmEndSec: Int, sceneSec: Int?, extra: List<SubtitleLine> = emptyList()): List<SubtitleLine> {
        val out = ArrayList<SubtitleLine>()
        var t = 5
        while (t < filmEndSec) { out.add(SubtitleLine(t * 1000L, t * 1000L + 2500L, "Line $t")); t += 4 }
        if (sceneSec != null) out.add(SubtitleLine(sceneSec * 1000L, sceneSec * 1000L + 3000L, "Scene line"))
        out.addAll(extra)
        return out
    }

    @Test fun subtitlesFindFirstLineAfterTheLongGap() {
        val filmEnd = (duration / 1000).toInt() - 9 * 60 // credits begin 9 min before the end
        val sceneAt = (duration / 1000).toInt() - 75     // scene 75 s before the end
        val found = SceneDetector.fromSubtitles(lines(filmEnd, sceneAt), duration, hasPost = true)
        assertNotNull(found)
        assertEquals(sceneAt * 1000L - 2_500L, found!!.startMs)
        assertTrue(found.confidence >= 45)
    }

    @Test fun subtitlesIgnoreAdvertLines() {
        val filmEnd = (duration / 1000).toInt() - 9 * 60
        val ad = SubtitleLine(duration - 20_000L, duration - 15_000L, "Subtitles by OpenSubtitles.org - rate this")
        val found = SceneDetector.fromSubtitles(lines(filmEnd, sceneSec = null, extra = listOf(ad)), duration, hasPost = true)
        assertNull(found)
    }

    @Test fun subtitlesNeedEnoughLines() {
        val few = listOf(SubtitleLine(100_000L, 102_000L, "hi"), SubtitleLine(duration - 60_000L, duration - 58_000L, "bye"))
        assertNull(SceneDetector.fromSubtitles(few, duration, hasPost = true))
    }

    @Test fun subtitlesIgnoreGapsFarFromTheEnd() {
        // A long quiet stretch in the middle of the film is not a credit scene.
        val out = ArrayList<SubtitleLine>()
        for (t in 5 until 4000 step 4) out.add(SubtitleLine(t * 1000L, t * 1000L + 2000L, "a $t"))
        for (t in 4300 until 8800 step 4) out.add(SubtitleLine(t * 1000L, t * 1000L + 2000L, "b $t"))
        assertNull(SceneDetector.fromSubtitles(out, duration, hasPost = true))
    }

    // ---- combining --------------------------------------------------------------------

    @Test fun nothingFoundMeansNoGuess() {
        assertNull(SceneDetector.combine(null, null))
    }

    @Test fun agreementRaisesConfidence() {
        val a = SceneCandidate(100_000L, 55)
        val s = SceneCandidate(103_000L, 60)
        val g = SceneDetector.combine(a, s)!!
        assertTrue(g.confidence > 60)
        assertTrue(g.confidence <= 95)
        assertTrue(g.usedAudio && g.usedSubtitles)
        assertEquals(100_000L, g.startMs)
    }

    @Test fun disagreementKeepsStrongerButLowersIt() {
        val a = SceneCandidate(100_000L, 55)
        val s = SceneCandidate(400_000L, 70)
        val g = SceneDetector.combine(a, s)!!
        assertEquals(400_000L, g.startMs)
        assertEquals(55, g.confidence)
        assertTrue(g.usedSubtitles && !g.usedAudio)
    }

    @Test fun singleSignalPassesThrough() {
        val g = SceneDetector.combine(SceneCandidate(5_000L, 50), null)!!
        assertEquals(50, g.confidence)
        assertTrue(g.usedAudio && !g.usedSubtitles)
    }

    // ---- remembered guess --------------------------------------------------------------

    @Test fun guessCodecRoundTrips() {
        val g = SceneGuess(1_234_567L, 72, usedAudio = true, usedSubtitles = false)
        assertEquals(g, SceneGuessCodec.decode(SceneGuessCodec.encode(g)))
    }

    @Test fun guessCodecRejectsGarbage() {
        assertNull(SceneGuessCodec.decode(null))
        assertNull(SceneGuessCodec.decode(""))
        assertNull(SceneGuessCodec.decode("1,2,3"))
        assertNull(SceneGuessCodec.decode("x,50,1,1"))
        assertNull(SceneGuessCodec.decode("100,150,1,1"))
        assertNull(SceneGuessCodec.decode("-5,50,1,1"))
    }

    // ---- two scenes / film envelope ---------------------------------------------------

    private val hopSec = 1000

    /** Credits music with a loud-music, silent, sound pattern repeated; returns a 1 s envelope. */
    private fun creditsEnvelope(startMs: Long, parts: List<Pair<Int, Float>>): AudioEnvelope {
        val v = ArrayList<Float>()
        for ((sec, level) in parts) repeat(sec) { v.add(level * (0.9f + 0.2f * ((it % 5) / 4f))) }
        return AudioEnvelope(startMs, hopSec, v.toFloatArray())
    }

    @Test fun audioFindsTwoScenes() {
        val start = duration - 10 * minute
        // 2 min music, 6 s silence, 40 s scene, 90 s music, 6 s silence, 60 s scene.
        val env = creditsEnvelope(start, listOf(120 to 0.1f, 6 to 0.0005f, 40 to 0.08f, 90 to 0.1f, 6 to 0.0005f, 60 to 0.08f))
        val found = SceneDetector.fromAudioAll(env, start, duration)
        assertEquals(2, found.size)
        assertTrue(found[0].startMs < found[1].startMs)
    }

    @Test fun combineAllKeepsOneWhenOneFlag() {
        val audio = listOf(SceneCandidate(1_000_000L, 50), SceneCandidate(1_500_000L, 40))
        val g = SceneDetector.combineAll(audio, emptyList(), hasMid = false, hasPost = true)
        assertEquals(1, g.size)
        assertEquals(1_500_000L, g[0].startMs) // post-credit: the latest of the near-best
    }

    @Test fun combineAllKeepsTwoWhenBothFlagged() {
        val audio = listOf(SceneCandidate(1_000_000L, 55), SceneCandidate(1_500_000L, 60))
        val subs = listOf(SceneCandidate(1_003_000L, 60))
        val g = SceneDetector.combineAll(audio, subs, hasMid = true, hasPost = true)
        assertEquals(2, g.size)
        assertTrue(g[0].startMs < g[1].startMs)
        assertTrue(g[0].usedAudio && g[0].usedSubtitles) // confirmed by both
    }

    @Test fun combineAllEmpty() {
        assertTrue(SceneDetector.combineAll(emptyList(), emptyList(), true, true).isEmpty())
    }

    @Test fun subtitlesAllFindsTwoGaps() {
        val out = ArrayList<SubtitleLine>()
        for (t in 5 until 8000 step 4) out.add(SubtitleLine(t * 1000L, t * 1000L + 2000L, "a $t"))
        out.add(SubtitleLine(8200_000L, 8203_000L, "scene one"))
        out.add(SubtitleLine(8500_000L, 8503_000L, "scene two"))
        val found = SceneDetector.fromSubtitlesAll(out, duration, hasMid = true, hasPost = true)
        assertEquals(2, found.size)
    }

    // ---- FilmEnvelope -----------------------------------------------------------------

    @Test fun envelopeCodecRoundTrips() {
        val db = floatArrayOf(-60f, Float.NaN, -12.4f, 3f)
        val back = FilmEnvelope.decode(FilmEnvelope.encode(db))
        assertEquals(-60f, back[0], 0.3f)
        assertTrue(back[1].isNaN())
        assertEquals(-12.4f, back[2], 0.3f)
        assertEquals(3f, back[3], 0.3f)
    }

    @Test fun barLevelsMarkUnmeasuredAndOrderLoudness() {
        val db = FloatArray(100) { if (it < 50) -20f else if (it < 80) -50f else Float.NaN }
        val bars = FilmEnvelope.barLevels(db, 10)
        assertTrue(bars[0] > bars[6])      // loud before quiet
        assertEquals(-1f, bars[9], 0f)     // not measured yet
        assertTrue(bars[0] in 0f..1f)
    }

    @Test fun isMeasuredChecksRange() {
        val db = floatArrayOf(-10f, -10f, Float.NaN, -10f)
        assertTrue(FilmEnvelope.isMeasured(db, 0, 2))
        assertTrue(!FilmEnvelope.isMeasured(db, 0, 4))
    }
}
