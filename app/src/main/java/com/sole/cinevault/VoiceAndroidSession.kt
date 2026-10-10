package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi

/** True when this phone can recognise speech offline with its own built-in recogniser. */
internal fun androidOnDeviceSpeechReady(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

/**
 * One tap-to-talk run with Android's own offline recogniser (Android 12 and up).
 * It does its own listening and decides when you've finished. Audio stays on the phone.
 * Everything here runs on the main thread. [onFinished] gets the words, or an
 * SpeechRecognizer error code (null words and a null code mean nothing was heard).
 */
internal class VoiceAndroidSession(
    private val context: Context,
    private val onLevel: (Float) -> Unit,
    private val onFinished: (text: String?, errorCode: Int?) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var finished = false

    @RequiresApi(Build.VERSION_CODES.S)
    fun start() {
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onError(error: Int) = finish(null, error)
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish(text, null)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        }
        r.startListening(intent)
    }

    /** Ends the listening now and uses what was heard so far. */
    fun stopNow() { runCatching { recognizer?.stopListening() } }

    fun cancel() {
        finished = true
        destroy()
    }

    private fun finish(text: String?, code: Int?) {
        if (finished) return
        finished = true
        destroy()
        onFinished(text, code)
    }

    private fun destroy() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
