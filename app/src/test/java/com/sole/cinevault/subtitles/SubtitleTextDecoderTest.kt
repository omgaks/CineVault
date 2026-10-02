package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class SubtitleTextDecoderTest {
    @Test fun utf8WithBomDecodesCleanly() {
        val text = "Talofa – mālō"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }

    @Test fun utf16LeWithBomDecodesCleanly() {
        val text = "字幕 عربي"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }

    @Test fun bomlessUtf16LeIsDetected() {
        val text = "Hello subtitle world"
        assertEquals(text, SubtitleTextDecoder.decode(text.toByteArray(Charsets.UTF_16LE)))
    }

    @Test fun windows1252FallsBackWithoutReplacementCharacters() {
        val text = "Café “subtitle” £5"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))
        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }
}
