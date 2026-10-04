package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSubtitleExtractorTest {

    @Test
    fun subripSample_usesStartTimeAndDurationFromEndTimecode() {
        val data = "1\n00:00:00,000 --> 00:00:02,500\nHello\nworld".toByteArray()
        val cue = EmbeddedSubtitleExtractor.parseSubripSample(61_000_000L, data)!!
        assertEquals(61_000L, cue.startMs)
        assertEquals(63_500L, cue.endMs)
        assertEquals("Hello\nworld", cue.text)
    }

    @Test
    fun vttSample_isParsedLikeSubrip() {
        val data = "WEBVTT\n\n00:00:00.000 --> 00:00:01.200\nHi".toByteArray()
        val cue = EmbeddedSubtitleExtractor.parseVttSample(5_000_000L, data)!!
        assertEquals(5_000L, cue.startMs)
        assertEquals(6_200L, cue.endMs)
    }

    @Test
    fun assSample_stripsStylingAndReadsDuration() {
        val data = ("Dialogue: 0:00:00:00,0:00:03:50,0,0,Default,,0,0,0,,{\\an8\\i1}Look out!\\NNow").toByteArray()
        val cue = EmbeddedSubtitleExtractor.parseAssSample(10_000_000L, data)!!
        assertEquals(10_000L, cue.startMs)
        assertEquals(13_500L, cue.endMs)
        assertEquals("Look out!\nNow", cue.text)
    }

    @Test
    fun assSample_skipsVectorDrawings() {
        val data = "Dialogue: 0:00:00:00,0:00:01:00,0,0,Default,,0,0,0,,{\\p1}m 0 0 l 10 10".toByteArray()
        assertNull(EmbeddedSubtitleExtractor.parseAssSample(0L, data))
    }

    @Test
    fun assText_keepsCommasInTheDialogue() {
        val data = "Dialogue: 0:00:00:00,0:00:01:00,0,0,Default,,0,0,0,,Well, well, well".toByteArray()
        assertEquals("Well, well, well", EmbeddedSubtitleExtractor.parseAssSample(0L, data)!!.text)
    }

    @Test
    fun tx3g_readsLengthPrefixedText() {
        val text = "Bonjour".toByteArray()
        val data = byteArrayOf(0, text.size.toByte()) + text + byteArrayOf(0, 0, 0, 8)
        assertEquals("Bonjour", EmbeddedSubtitleExtractor.parseTx3gText(data))
        assertEquals("", EmbeddedSubtitleExtractor.parseTx3gText(byteArrayOf(0, 0)))
    }

    @Test
    fun timecodeAndSrt_areFormattedCorrectly() {
        assertEquals("01:02:03,004", EmbeddedSubtitleExtractor.timecode(3_723_004L))
        val srt = EmbeddedSubtitleExtractor.toSrt(
            listOf(RawCue(2000, 3000, "B"), RawCue(0, 1000, "A"))
        )
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nA\n\n2\n00:00:02,000 --> 00:00:03,000\nB\n\n", srt)
        // and our own parser can read it back
        assertEquals(2, SubtitleCueParser.parse(srt).size)
    }

    @Test
    fun chooseTrack_prefersPositionWhenLanguageAgrees() {
        val tracks = listOf(
            EmbeddedSubtitleTrack("eng", null, listOf(RawCue(0, 1, "a"))),
            EmbeddedSubtitleTrack("hin", null, listOf(RawCue(0, 1, "b"))),
        )
        assertEquals("hin", EmbeddedSubtitleExtractor.chooseTrack(tracks, EmbeddedSubtitleRef(1, "hi"))!!.language)
    }

    @Test
    fun chooseTrack_fallsBackToSameLanguageWhenOrderDiffers() {
        val tracks = listOf(
            EmbeddedSubtitleTrack("eng", null, listOf(RawCue(0, 1, "a"))),
            EmbeddedSubtitleTrack("hin", null, listOf(RawCue(0, 1, "b"))),
        )
        // A closed-caption track shifted the ordinal: asks for position 2, language Hindi.
        assertEquals("hin", EmbeddedSubtitleExtractor.chooseTrack(tracks, EmbeddedSubtitleRef(2, "hi"))!!.language)
        assertNotNull(EmbeddedSubtitleExtractor.chooseTrack(tracks, EmbeddedSubtitleRef(5, null)))
    }

    @Test
    fun selectedKey_isParsed() {
        assertEquals(2, EmbeddedSubtitleRef.fromSelectedKey("embedded:2:0", "en")!!.ordinal)
        assertNull(EmbeddedSubtitleRef.fromSelectedKey("downloaded", "en"))
        assertNull(EmbeddedSubtitleRef.fromSelectedKey(null, null))
        assertTrue(EmbeddedSubtitleRef.fromSelectedKey("embedded:0:0", null) != null)
    }
}
