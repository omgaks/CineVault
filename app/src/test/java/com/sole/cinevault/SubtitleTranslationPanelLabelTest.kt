package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTranslationPanelLabelTest {

    @Test
    fun translatedFileNameBecomesFriendly() {
        val raw = "_bonkai77_.Ghost.in.the.Shell._1995_._BD.1080p.Dual.Audio.x265.HEVC.10bit_-translated-hi-1791130641413.srt"
        assertEquals("AI translated · Hindi", friendlySourceLabel(raw))
    }

    @Test
    fun speechFileNameBecomesFriendly() {
        assertEquals("Speech-to-subs · English", friendlySourceLabel("Movie-ai-en-1791130641413.srt"))
    }

    @Test
    fun shortAndLongOtherLabels() {
        assertEquals("Hindi", friendlySourceLabel("Hindi"))
        val long = "x".repeat(60)
        assertEquals("x".repeat(40) + "…", friendlySourceLabel(long))
    }
}
