package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueParserTest {

    @Test
    fun parsesPlainSrt() {
        val cues = SubtitleCueParser.parse(
            "1\n00:00:01,000 --> 00:00:03,500\nHello\nworld\n\n2\n00:01:00,250 --> 00:01:02,000\nBye\n"
        )
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("Hello\nworld", cues[0].text)
        assertEquals(60250L, cues[1].startMs)
    }

    @Test
    fun parsesCrLfBomAndMissingBlankLines() {
        val cues = SubtitleCueParser.parse(
            "\uFEFF1\r\n00:00:01,000 --> 00:00:02,000\r\nOne\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nTwo\r\n"
        )
        assertEquals(listOf("One", "Two"), cues.map { it.text })
    }

    @Test
    fun parsesWebVttWithHeaderAndSettings() {
        val cues = SubtitleCueParser.parse(
            "WEBVTT\n\n00:01.500 --> 00:03.000 align:start position:0%\nHi there\n\n01:00:00.000 --> 01:00:01.000\nLate\n"
        )
        assertEquals(2, cues.size)
        assertEquals(1500L, cues[0].startMs)
        assertEquals(3_600_000L, cues[1].startMs)
    }

    @Test
    fun keepsFontColourMarkupAndStripsAlignmentTags() {
        val cues = SubtitleCueParser.parse(
            "1\n00:00:01,000 --> 00:00:02,000\n{\\an8}Top\n<font color=\"#00E5FF\">Segundo</font>\n"
        )
        assertEquals("Top\n<font color=\"#00E5FF\">Segundo</font>", cues[0].text)
    }

    @Test
    fun fixesBadEndAndSortsByStart() {
        val cues = SubtitleCueParser.parse(
            "1\n00:00:09,000 --> 00:00:09,000\nB\n\n2\n00:00:01,000 --> 00:00:02,000\nA\n"
        )
        assertEquals(listOf("A", "B"), cues.map { it.text })
        assertTrue(cues[1].endMs > cues[1].startMs)
    }

    @Test
    fun garbageProducesNoCuesAndNeverThrows() {
        assertTrue(SubtitleCueParser.parse("").isEmpty())
        assertTrue(SubtitleCueParser.parse("hello\nnot a subtitle\n").isEmpty())
        assertTrue(SubtitleCueParser.parse("00:00:xx --> 00:00:02,000\nText").isEmpty())
    }
}
