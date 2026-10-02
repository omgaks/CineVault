package com.sole.cinevault.subtitles

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/** Single byte-to-text policy for subtitle imports and transforms. */
object SubtitleTextDecoder {
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }

        val sample = minOf(bytes.size, 512)
        var evenNuls = 0
        var oddNuls = 0
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) {
                if (i % 2 == 0) evenNuls++ else oddNuls++
            }
        }
        if (oddNuls >= 2 && oddNuls > evenNuls * 2) return bytes.toString(Charsets.UTF_16LE)
        if (evenNuls >= 2 && evenNuls > oddNuls * 2) return bytes.toString(Charsets.UTF_16BE)

        val strictUtf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        return strictUtf8 ?: bytes.toString(Charset.forName("windows-1252"))
    }
}
