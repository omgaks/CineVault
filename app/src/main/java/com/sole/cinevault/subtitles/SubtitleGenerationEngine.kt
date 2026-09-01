package com.sole.cinevault.subtitles

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Generates subtitles from a video's own audio track, fully on-device:
 *
 *   AutoSyncAudioExtractor (already existed, built for Auto-Sync)
 *     -> 16kHz mono PCM, in bounded-size chunks
 *   Silero VAD (already existed, same model already bundled for Auto-Sync)
 *     -> discrete speech segments, so Whisper only ever transcribes actual
 *        dialogue rather than wasting a pass on music/silence
 *   Whisper Base INT8 via sherpa-onnx (optional — see WhisperModelManager)
 *     -> text per segment
 *   -> assembled into a standard .srt
 *
 * Deliberately chunks the audio extraction (CHUNK_DURATION_MS at a time)
 * rather than decoding an entire 2-hour film into one PCM array up front —
 * AutoSyncAudioExtractor's own doc notes a 5-minute window costs ~19MB;
 * the whole film at once would be several hundred MB for one allocation.
 * The SAME Vad instance is kept alive across every chunk (not recreated
 * per chunk) specifically because sherpa-onnx's VAD tracks segment.start
 * as a running sample count since the instance was created — recreating it
 * per chunk would reset that counter and corrupt every timestamp after the
 * first chunk.
 */
object SubtitleGenerationEngine {

    data class Progress(val phase: String, val percent: Int)

    sealed class Result {
        data class Success(val srtText: String, val detectedLanguage: String?, val cueCount: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    private const val CHUNK_DURATION_MS = 4 * 60 * 1000L // 4-minute extraction windows
    private const val VAD_WINDOW_SAMPLES = 512

    suspend fun generate(
        context: Context,
        filePath: String,
        trackLanguage: String?,
        videoDurationMs: Long,
        onProgress: (Progress) -> Unit
    ): Result {
        if (videoDurationMs <= 0L) return Result.Failed("Unknown video duration.")
        if (!WhisperModelManager.isModelReady(context)) {
            return Result.Failed(WhisperModelManager.setupInstructions(context))
        }

        val recognizer = WhisperModelManager.createRecognizer(context)
            ?: return Result.Failed("Couldn't initialize the speech recognizer from the installed model files.")

        val vadConfig = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                // Slightly more tolerant than Auto-Sync's own VAD tuning
                // (AutoSyncEngine.kt) — that one only needs a rough speech
                // timeline to correlate against subtitle cues; this one's
                // output boundaries become the actual subtitle cue timing,
                // so cutting too eagerly mid-sentence is more costly here.
                minSilenceDuration = 0.4f,
                minSpeechDuration = 0.3f,
                windowSize = VAD_WINDOW_SAMPLES,
                // Caps one continuous speech run fed to Whisper in a single
                // pass. Whisper's own encoder window is 30s; capping well
                // under that keeps every segment comfortably within it.
                maxSpeechDuration = 20f,
            ),
            sampleRate = 16_000,
            numThreads = 1,
            provider = "cpu",
        )
        val vad = Vad(assetManager = context.assets, config = vadConfig)

        val srt = StringBuilder()
        var cueIndex = 1
        var detectedLanguage: String? = null

        try {
            var chunkStartMs = 0L
            while (chunkStartMs < videoDurationMs) {
                currentCoroutineContext().ensureActive()
                val chunkDurationMs = CHUNK_DURATION_MS.coerceAtMost(videoDurationMs - chunkStartMs)
                onProgress(
                    Progress(
                        phase = "Transcribing",
                        percent = ((chunkStartMs * 100) / videoDurationMs).toInt().coerceIn(0, 99)
                    )
                )

                val audio = AutoSyncAudioExtractor.extractWindow(
                    context = context,
                    filePath = filePath,
                    trackLanguage = trackLanguage,
                    startMs = chunkStartMs,
                    durationMs = chunkDurationMs,
                    targetSampleRate = 16_000,
                )

                if (audio != null && audio.samples.isNotEmpty()) {
                    var sampleIdx = 0
                    while (sampleIdx + VAD_WINDOW_SAMPLES <= audio.samples.size) {
                        currentCoroutineContext().ensureActive()
                        vad.acceptWaveform(audio.samples.copyOfRange(sampleIdx, sampleIdx + VAD_WINDOW_SAMPLES))
                        sampleIdx += VAD_WINDOW_SAMPLES

                        while (!vad.empty()) {
                            currentCoroutineContext().ensureActive()
                            val segment = vad.front()
                            vad.pop()
                            cueIndex = transcribeSegment(
                                recognizer = recognizer,
                                segment = segment,
                                sampleRate = audio.sampleRate,
                                cueIndex = cueIndex,
                                srt = srt,
                                onLanguageDetected = { if (detectedLanguage == null) detectedLanguage = it }
                            )
                        }
                    }
                }
                // No mid-track chunk boundary chops here — vad is fed
                // continuously across chunks and its own state (not this
                // loop) decides where a speech segment actually ends. Only
                // the trailing partial segment at end-of-track needs an
                // explicit flush, done once below after the loop.
                chunkStartMs += chunkDurationMs
            }

            vad.flush()
            while (!vad.empty()) {
                currentCoroutineContext().ensureActive()
                val segment = vad.front()
                vad.pop()
                cueIndex = transcribeSegment(
                    recognizer = recognizer,
                    segment = segment,
                    sampleRate = 16_000,
                    cueIndex = cueIndex,
                    srt = srt,
                    onLanguageDetected = { if (detectedLanguage == null) detectedLanguage = it }
                )
            }
        } finally {
            vad.release()
            recognizer.release()
        }

        val cueCount = cueIndex - 1
        if (cueCount == 0) {
            return Result.Failed("No speech detected — the audio track may be silent, music-only, or in a format the recognizer couldn't read.")
        }
        onProgress(Progress("Done", 100))
        return Result.Success(srtText = srt.toString().trim() + "\n", detectedLanguage = detectedLanguage, cueCount = cueCount)
    }

    /** Returns the next cue index to use (unchanged if the segment produced no usable text). */
    private fun transcribeSegment(
        recognizer: com.k2fsa.sherpa.onnx.OfflineRecognizer,
        segment: com.k2fsa.sherpa.onnx.SpeechSegment,
        sampleRate: Int,
        cueIndex: Int,
        srt: StringBuilder,
        onLanguageDetected: (String) -> Unit,
    ): Int {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(segment.samples, sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            val text = result.text.trim()
            if (text.isEmpty()) return cueIndex

            if (result.lang.isNotBlank()) onLanguageDetected(result.lang)

            // segment.start is a running sample count since the Vad
            // instance was created (see sherpa-onnx's
            // voice-activity-detector.cc — buffer_.Tail()-based, not reset
            // per acceptWaveform call), so no chunk-offset math is needed
            // here — it already maps directly to absolute track time.
            val startMs = segment.start.toLong() * 1000L / sampleRate
            val endMs = startMs + (segment.samples.size.toLong() * 1000L / sampleRate)

            return appendFormattedCues(
                srt = srt,
                firstCueIndex = cueIndex,
                text = text,
                segmentStartMs = startMs,
                segmentEndMs = endMs,
            )
        } finally {
            stream.release()
        }
    }

    /**
     * Whisper can return a whole paragraph for one long VAD speech segment.
     * Normal subtitles should stay glanceable, so split that paragraph into
     * compact cues and distribute the original speech timing proportionally.
     *
     * Target: roughly two short lines / <= 84 characters per cue.
     */
    private fun appendFormattedCues(
        srt: StringBuilder,
        firstCueIndex: Int,
        text: String,
        segmentStartMs: Long,
        segmentEndMs: Long,
    ): Int {
        val pieces = splitSubtitleText(text)
        if (pieces.isEmpty()) return firstCueIndex

        val totalDuration = (segmentEndMs - segmentStartMs).coerceAtLeast(1L)
        val weights = pieces.map { piece -> piece.length.coerceAtLeast(1) }
        val totalWeight = weights.sum().coerceAtLeast(1)

        var cueIndex = firstCueIndex
        var consumedWeight = 0

        pieces.forEachIndexed { index, piece ->
            val cueStartMs =
                segmentStartMs + (totalDuration * consumedWeight / totalWeight)

            consumedWeight += weights[index]

            val cueEndMs =
                if (index == pieces.lastIndex) {
                    segmentEndMs
                } else {
                    segmentStartMs + (totalDuration * consumedWeight / totalWeight)
                }

            srt.append(cueIndex).append('\n')
            srt.append(formatSrtTimestamp(cueStartMs))
                .append(" --> ")
                .append(formatSrtTimestamp(cueEndMs.coerceAtLeast(cueStartMs + 1L)))
                .append('\n')
            srt.append(wrapSubtitleLines(piece)).append("\n\n")
            cueIndex++
        }

        return cueIndex
    }

    private fun splitSubtitleText(text: String): List<String> {
        val normalized = text
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return emptyList()

        val sentenceChunks = normalized
            .split(Regex("(?<=[.!?…])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val output = mutableListOf<String>()

        for (sentence in sentenceChunks) {
            if (sentence.length <= 84) {
                output += sentence
                continue
            }

            val words = sentence.split(' ').filter { it.isNotBlank() }
            val current = StringBuilder()

            for (word in words) {
                val proposedLength =
                    if (current.isEmpty()) word.length
                    else current.length + 1 + word.length

                if (proposedLength > 84 && current.isNotEmpty()) {
                    output += current.toString()
                    current.clear()
                }

                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }

            if (current.isNotEmpty()) output += current.toString()
        }

        return output
    }

    private fun wrapSubtitleLines(text: String): String {
        if (text.length <= 42) return text

        val words = text.split(' ')
        val first = StringBuilder()
        val second = StringBuilder()

        for (word in words) {
            if (first.length < 42) {
                val next = if (first.isEmpty()) word else "${first} $word"
                if (next.length <= 42 || second.isEmpty()) {
                    if (first.isNotEmpty()) first.append(' ')
                    first.append(word)
                    continue
                }
            }

            if (second.isNotEmpty()) second.append(' ')
            second.append(word)
        }

        return if (second.isEmpty()) first.toString()
        else first.toString() + "\n" + second.toString()
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000
        val minutes = (clamped % 3_600_000) / 60_000
        val seconds = (clamped % 60_000) / 1_000
        val millis = clamped % 1_000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
