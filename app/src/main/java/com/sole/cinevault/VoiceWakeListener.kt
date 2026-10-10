package com.sole.cinevault

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

private const val SAMPLE_RATE = 16000
private const val WINDOW_SAMPLES = 512
private const val WINDOW_MS = 32
private const val PRE_ROLL_WINDOWS = 16          // about half a second of audio before speech starts
private const val COOLDOWN_MS = 2000L
private const val RECENT_WINDOWS = 80            // about 2.5 seconds
private const val MODEL_FOLDER = "voice_kws_v1"
private val MODEL_FILES = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt", "keywords.txt")

/** Copies the bundled wake-word model to app storage once, so the engine can read plain files. */
private fun prepareModelFolder(context: Context): File {
    val dir = File(context.filesDir, MODEL_FOLDER)
    if (!dir.exists()) dir.mkdirs()
    MODEL_FILES.forEach { name ->
        val out = File(dir, name)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open("kws/$name").use { input ->
                val tmp = File(dir, "$name.part")
                tmp.outputStream().use { input.copyTo(it) }
                tmp.renameTo(out)
            }
        }
    }
    return dir
}

/**
 * Listens for the wake phrases on one background thread. Speech detection gates
 * the work: when nobody is speaking the wake-word model is not run at all.
 * Audio is only analysed on this device and is never stored or sent anywhere.
 */
internal class VoiceWakeListener(
    private val context: Context,
    private val phraseIds: () -> Set<String>,
    private val onWake: (VoiceWakePhrase?, String) -> Unit,
    private val onStatus: (listening: Boolean, error: String?) -> Unit
) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val lock = Any()

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            val previous = thread
            thread = Thread({
                runCatching { previous?.join(1500) }
                loop()
            }, "voice-wake").also { it.isDaemon = true; it.start() }
        }
    }

    fun stop() {
        synchronized(lock) { running = false }
    }

    // The microphone permission is checked by VoiceWakeHost before start() is ever called.
    @SuppressLint("MissingPermission")
    private fun loop() {
        var record: AudioRecord? = null
        var spotter: KeywordSpotter? = null
        var vad: Vad? = null
        var stream: OnlineStream? = null
        try {
            val dir = prepareModelFolder(context)
            fun path(n: String) = File(dir, n).absolutePath
            spotter = KeywordSpotter(
                assetManager = null,
                config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = path("encoder.int8.onnx"),
                            decoder = path("decoder.int8.onnx"),
                            joiner = path("joiner.int8.onnx")
                        ),
                        tokens = path("tokens.txt"),
                        numThreads = 1,
                        provider = "cpu",
                        modelType = "zipformer2"
                    ),
                    maxActivePaths = 4,
                    keywordsFile = path("keywords.txt"),
                    keywordsScore = 2.0f,
                    keywordsThreshold = 0.1f,
                    numTrailingBlanks = 1
                )
            )
            vad = Vad(
                assetManager = context.assets,
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "silero_vad.onnx",
                        threshold = 0.5f,
                        minSilenceDuration = 0.25f,
                        minSpeechDuration = 0.1f,
                        windowSize = WINDOW_SAMPLES
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu"
                )
            )

            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw IllegalStateException("This phone could not open its microphone")
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, WINDOW_SAMPLES * 2 * 8)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("The microphone is busy or not allowed")
            record.startRecording()
            onStatus(true, null)

            val gate = VoiceGate()
            val preRoll = ArrayDeque<FloatArray>()
            val recent = ArrayDeque<FloatArray>()      // about 2.5 s of the latest sound, for the speech check
            val shorts = ShortArray(WINDOW_SAMPLES)
            val stats = VoiceRuntime.stats
            var wasOpen = false
            var lastWakeAt = 0L

            while (running) {
                var read = 0
                while (read < WINDOW_SAMPLES && running) {
                    val n = record.read(shorts, read, WINDOW_SAMPLES - read)
                    if (n <= 0) throw IllegalStateException("The microphone stopped responding")
                    read += n
                }
                if (read < WINDOW_SAMPLES) break
                val window = FloatArray(WINDOW_SAMPLES) { shorts[it] / 32768f }
                recent.addLast(window)
                while (recent.size > RECENT_WINDOWS) recent.removeFirst()

                val tStart = System.nanoTime()
                val probability = vad.compute(window)
                val open = gate.update(probability, WINDOW_MS)
                stats.addListened(WINDOW_MS)

                if (!open) {
                    if (wasOpen) { stream?.release(); stream = null }
                    wasOpen = false
                    preRoll.addLast(window)
                    while (preRoll.size > PRE_ROLL_WINDOWS) preRoll.removeFirst()
                    stats.addProcessing(System.nanoTime() - tStart)
                    continue
                }

                stats.addGated(WINDOW_MS)
                if (!wasOpen) {
                    // Speech just began: start a fresh stream with the phrases ticked right now,
                    // and give it the half second before, so the start of "Hey" is not lost.
                    val keywords = wakeKeywordLines(phraseIds())
                    stream?.release()
                    val fresh = spotter.createStream(keywords)
                    stream = fresh
                    preRoll.forEach { fresh.acceptWaveform(it, SAMPLE_RATE) }
                    preRoll.clear()
                    wasOpen = true
                }
                val s = stream!!
                s.acceptWaveform(window, SAMPLE_RATE)
                while (spotter.isReady(s)) {
                    spotter.decode(s)
                    val result = spotter.getResult(s)
                    if (result.keyword.isNotEmpty()) {
                        spotter.reset(s)
                        val now = System.currentTimeMillis()
                        if (now - lastWakeAt >= COOLDOWN_MS) {
                            lastWakeAt = now
                            val phrase = phraseForKeywordName(result.keyword)
                            if (phrase != null) stats.addHear(phrase.id)
                            val snapshot = FloatArray(recent.size * WINDOW_SAMPLES)
                            recent.forEachIndexed { i, w -> System.arraycopy(w, 0, snapshot, i * WINDOW_SAMPLES, WINDOW_SAMPLES) }
                            VoiceRuntime.wakeAudio = snapshot
                            onWake(phrase, result.keyword)
                        }
                    }
                }
                stats.addProcessing(System.nanoTime() - tStart)
            }
        } catch (t: Throwable) {
            onStatus(false, t.message ?: t.javaClass.simpleName)
            return
        } finally {
            runCatching { stream?.release() }
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { vad?.release() }
            runCatching { spotter?.release() }
        }
        onStatus(false, null)
    }
}
