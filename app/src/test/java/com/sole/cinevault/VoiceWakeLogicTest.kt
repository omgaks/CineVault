package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWakeLogicTest {
    @Test fun defaultsAreHeyCineVaultAndHeyVault() {
        assertEquals(setOf("hey_cinevault", "hey_vault"), defaultWakePhraseIds())
    }

    @Test fun keywordLinesFollowTheTicks() {
        val lines = wakeKeywordLines(setOf("hey_vault")).lines()
        assertEquals(listOf("▁HE Y ▁VA UL T @HEY_VAULT"), lines)
        val both = wakeKeywordLines(setOf("hey_cinevault", "hey_vault")).lines()
        assertEquals(4, both.size)
        assertTrue(both.all { " @" in it })
        assertFalse(both.any { it.endsWith("@VAULT") })
    }

    @Test fun nothingTickedFallsBackToTheDefaults() {
        assertEquals(wakeKeywordLines(defaultWakePhraseIds()), wakeKeywordLines(emptySet()))
    }

    @Test fun everyKeywordNameMapsBackToItsPhrase() {
        VoiceWakePhrase.values().forEach { phrase ->
            phrase.keywords.forEach { (_, name) -> assertEquals(phrase, phraseForKeywordName(name)) }
        }
        assertEquals(VoiceWakePhrase.HeyVault, phraseForKeywordName("hey_vault"))
        assertNull(phraseForKeywordName("SOMETHING_ELSE"))
    }

    @Test fun keywordNamesAreUnique() {
        val names = VoiceWakePhrase.values().flatMap { p -> p.keywords.map { it.second } }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun gateOpensOnSpeechAndStaysOpenThroughTheHangover() {
        val gate = VoiceGate(openProbability = 0.5f, hangoverMs = 100)
        assertFalse(gate.update(0.1f, 32))
        assertTrue(gate.update(0.9f, 32))
        assertTrue(gate.update(0.1f, 32))   // 68 ms left
        assertTrue(gate.update(0.1f, 32))   // 36 ms left
        assertTrue(gate.update(0.1f, 32))   // 4 ms left
        assertFalse(gate.update(0.1f, 32))  // closed
        assertTrue(gate.update(0.7f, 32))
        gate.reset()
        assertFalse(gate.isOpen)
    }

    @Test fun statsShareAndReset() {
        val stats = VoiceStats()
        assertEquals(0.0, stats.processingShare(), 0.0)
        repeat(1000) { stats.addListened(10) }          // 10 s
        repeat(100) { stats.addGated(10) }              // 1 s of speech-like sound
        stats.addProcessing(50_000_000L)                // 50 ms of work
        assertEquals(0.1, stats.speechShare(), 1e-9)
        assertEquals(0.005, stats.processingShare(), 1e-9)
        stats.addHear("hey_vault"); stats.addHear("hey_vault"); stats.addHear("vault")
        assertEquals(2, stats.hearsFor("hey_vault"))
        assertEquals(3, stats.totalHears)
        stats.reset()
        assertEquals(0L, stats.listenedMs)
        assertEquals(0, stats.totalHears)
    }

    @Test fun readoutFormatting() {
        assertEquals("45s", formatListened(45_000))
        assertEquals("12m 3s", formatListened(723_000))
        assertEquals("2h 5m", formatListened(7_500_000))
        assertEquals("0%", formatPercent(0.0))
        assertEquals("<0.1%", formatPercent(0.0004))
        assertEquals("0.5%", formatPercent(0.005))
        assertEquals("12%", formatPercent(0.12))
    }

    @Test fun autoGainLiftsQuietMicButNeverAttenuates() {
        val g = VoiceAutoGain()
        var gain = 1f
        repeat(2000) { gain = g.gainFor(0.02f) }
        assertTrue("quiet mic should be amplified, got $gain", gain > 5f)
        val loud = VoiceAutoGain()
        var loudGain = 0f
        repeat(50) { loudGain = loud.gainFor(0.9f) }
        assertEquals(1f, loudGain, 0.001f)
    }

    @Test fun peakTracksLoudestAndResets() {
        val stats = VoiceStats()
        stats.addPeak(0.1f); stats.addPeak(0.4f); stats.addPeak(0.2f)
        assertEquals(0.4f, stats.peak, 0.0001f)
        stats.reset()
        assertEquals(0f, stats.peak, 0f)
    }
}
