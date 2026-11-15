package com.sole.cinevault.audiofx

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lets Audio Studio look at the sound the player has decoded, inside the app.
 * Unlike android.media.audiofx.Visualizer it needs no microphone permission, because
 * it never touches the device's audio capture: it only reads the player's own PCM.
 */
internal object PcmTap {
    /** waveform = unsigned 8-bit centred at 128, fft = Visualizer-style (re, im) bytes. */
    @Volatile
    var listener: ((waveform: ByteArray, fft: ByteArray, sampleRateHz: Int) -> Unit)? = null
}

/** Passes audio through untouched while handing a snapshot to [PcmTap.listener] ~20 times a second. */
@OptIn(UnstableApi::class)
internal class PcmTapAudioProcessor : BaseAudioProcessor() {
    private val ring = FloatArray(PCM_TAP_SIZE)
    private var ringPos = 0
    private var filled = 0
    private var lastEmitMs = 0L
    private var channels = 2
    private var sampleRate = 48000
    private var encoding = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET // inactive for anything else
        }
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate
        encoding = inputAudioFormat.encoding
        ringPos = 0; filled = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (PcmTap.listener != null) {
            runCatching { sample(inputBuffer.duplicate().order(ByteOrder.nativeOrder())) }
        }
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    private fun sample(buf: ByteBuffer) {
        val bytesPer = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPer * channels
        while (buf.remaining() >= frameBytes) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += if (encoding == C.ENCODING_PCM_FLOAT) buf.float else buf.short / 32768f
            }
            ring[ringPos] = sum / channels
            ringPos = (ringPos + 1) % PCM_TAP_SIZE
            if (filled < PCM_TAP_SIZE) filled++
        }
        val now = System.nanoTime() / 1_000_000L
        if (filled < PCM_TAP_SIZE || now - lastEmitMs < 50L) return
        lastEmitMs = now
        val ordered = FloatArray(PCM_TAP_SIZE) { ring[(ringPos + it) % PCM_TAP_SIZE] }
        val (wave, fft) = buildTapFrames(ordered)
        PcmTap.listener?.invoke(wave, fft, sampleRate)
    }

    override fun onFlush() { ringPos = 0; filled = 0 }
    override fun onReset() { ringPos = 0; filled = 0 }
}
