package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveAudioDecoderStatusTest {

    @Test
    fun ffmpegDecoderNameIsClassifiedAsFfmpeg() {
        assertEquals(
            ActiveAudioDecoderKind.FFMPEG,
            classifyActiveAudioDecoder("ffmpegAudioDecoder"),
        )
    }

    @Test
    fun libavDecoderNameIsClassifiedAsFfmpeg() {
        assertEquals(
            ActiveAudioDecoderKind.FFMPEG,
            classifyActiveAudioDecoder("libavcodec.dca"),
        )
    }

    @Test
    fun androidCodecNameIsClassifiedAsPlatform() {
        assertEquals(
            ActiveAudioDecoderKind.PLATFORM,
            classifyActiveAudioDecoder("c2.android.eac3.decoder"),
        )
    }

    @Test
    fun vendorCodecNameIsClassifiedAsPlatform() {
        assertEquals(
            ActiveAudioDecoderKind.PLATFORM,
            classifyActiveAudioDecoder("OMX.qcom.audio.decoder.ac3"),
        )
    }

    @Test
    fun blankDecoderNameRemainsUnknown() {
        assertEquals(
            ActiveAudioDecoderKind.UNKNOWN,
            classifyActiveAudioDecoder("   "),
        )
    }

    @Test
    fun missingDecoderNameRemainsUnknown() {
        assertEquals(
            ActiveAudioDecoderKind.UNKNOWN,
            classifyActiveAudioDecoder(null),
        )
    }
}
