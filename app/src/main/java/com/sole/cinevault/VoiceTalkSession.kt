package com.sole.cinevault

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

private const val TALK_RATE = 16000
private const val TALK_WINDOW = 512

/**
 * One tap-to-talk recording. Listens until the person stops speaking (or the
 * time limit), then hands back the audio. Nothing is saved to disk.
 * [onFinished] gets null when nobody spoke or the microphone failed.
 */
internal class VoiceTalkSession(
    private val context: Context,
    private val onLevel: (Float) -> Unit,
    private val onFinished: (samples: FloatArray?, error: String?) -> Unit
) {
    @Volatile private var stopRequested = false
    @Volatile private var cancelled = false

    /** Ends the recording now and uses what was heard so far. */
    fun stopNow() { stopRequested = true }
    fun cancel() { cancelled = true; stopRequested = true }

    fun start() {
        Thread({ run() }, "voice-talk").also { it.isDaemon = true; it.start() }
    }

    // The microphone permission is checked before the button can start a session.
    @SuppressLint("MissingPermission")
    private fun run() {
        var record: AudioRecord? = null
        var vad: Vad? = null
        var result: FloatArray? = null
        var error: String? = null
        try {
            vad = Vad(
                assetManager = context.assets,
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "silero_vad.onnx",
                        threshold = 0.5f,
                        minSilenceDuration = 0.25f,
                        minSpeechDuration = 0.1f,
                        windowSize = TALK_WINDOW
                    ),
                    sampleRate = TALK_RATE,
                    numThreads = 1,
                    provider = "cpu"
                )
            )
            val minBuffer = AudioRecord.getMinBufferSize(TALK_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw IllegalStateException("This phone could not open its microphone")
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, TALK_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, TALK_WINDOW * 2 * 8)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("The microphone is busy or not allowed")
            record.startRecording()

            val endpoint = SpeechEndpoint(windowMs = TALK_WINDOW * 1000 / TALK_RATE)
            val all = ArrayList<FloatArray>()
            val shorts = ShortArray(TALK_WINDOW)
            var spoke = false
            while (!stopRequested) {
                var read = 0
                while (read < TALK_WINDOW && !stopRequested) {
                    val n = record.read(shorts, read, TALK_WINDOW - read)
                    if (n <= 0) throw IllegalStateException("The microphone stopped responding")
                    read += n
                }
                if (read < TALK_WINDOW) break
                val window = FloatArray(TALK_WINDOW) { shorts[it] / 32768f }
                all.add(window)
                val p = vad.compute(window)
                onLevel(p)
                when (endpoint.update(p)) {
                    SpeechEndpoint.Status.Done -> { spoke = true; break }
                    SpeechEndpoint.Status.NoSpeech -> { spoke = false; all.clear(); break }
                    SpeechEndpoint.Status.Speaking -> spoke = true
                    SpeechEndpoint.Status.WaitingForSpeech -> Unit
                }
            }
            // Tapping the button to finish early keeps what was said so far.
            if (all.isNotEmpty() && (spoke || stopRequested)) {
                val out = FloatArray(all.size * TALK_WINDOW)
                all.forEachIndexed { i, w -> System.arraycopy(w, 0, out, i * TALK_WINDOW, TALK_WINDOW) }
                result = out
            }
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { vad?.release() }
        }
        if (!cancelled) onFinished(result, error)
    }
}
