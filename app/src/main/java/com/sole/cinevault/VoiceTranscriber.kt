package com.sole.cinevault

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.sole.cinevault.subtitles.WhisperModelManager

private const val RECOGNIZER_KEEP_MS = 120_000L
private const val TALK_SAMPLE_RATE = 16000

/**
 * Turns a short recording into text with the Whisper model already installed
 * for subtitles. The loaded model is kept for about two minutes so a second
 * command is quick, then released to give the memory back.
 */
internal object VoiceTranscriber {
    private val lock = Any()
    private var recognizer: OfflineRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val release = Runnable {
        synchronized(lock) { runCatching { recognizer?.release() }; recognizer = null }
    }

    fun isReady(context: Context): Boolean = WhisperModelManager.isModelReady(context)

    /** Blocking. Call from a background thread. Returns null if the model could not be loaded. */
    fun transcribe(context: Context, samples: FloatArray): String? = synchronized(lock) {
        handler.removeCallbacks(release)
        try {
            val r = recognizer ?: WhisperModelManager.createRecognizer(context.applicationContext, "en")?.also { recognizer = it }
                ?: return null
            val stream = r.createStream()
            try {
                stream.acceptWaveform(samples, TALK_SAMPLE_RATE)
                r.decode(stream)
                r.getResult(stream).text
            } finally {
                stream.release()
            }
        } finally {
            handler.postDelayed(release, RECOGNIZER_KEEP_MS)
        }
    }
}
