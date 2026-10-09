package com.sole.cinevault.segments

import android.content.Context
import android.net.Uri
import com.sole.cinevault.subtitles.AutoSyncAudioExtractor
import com.sole.cinevault.subtitles.SubtitleCueParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Marks a scene segment that CineVault guessed itself, as "detected:audio+subtitles". */
const val DETECTED_SOURCE = "detected"

fun isDetectedSegment(segment: SmartSegment): Boolean = segment.source.startsWith(DETECTED_SOURCE)

/** "audio + subtitles" for the notice; null when the segment was not guessed. */
fun detectedEvidenceLabel(segment: SmartSegment): String? {
    if (!isDetectedSegment(segment)) return null
    return segment.source.substringAfter(':', "").split('+').filter { it.isNotBlank() }
        .joinToString(" + ").ifBlank { null }
}

/**
 * Runs the audio and subtitle analysis for the end of a film. Local files only: the audio
 * extractor cannot read network shares or streams. Never throws.
 */
class SceneDetectionRunner(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cinevault_scene_guess", Context.MODE_PRIVATE)

    fun cached(key: String): SceneGuess? = SceneGuessCodec.decode(prefs.getString(key, null))

    suspend fun detect(
        key: String,
        filePath: String,
        durationMs: Long,
        creditsStartMs: Long,
        subtitleUri: Uri?,
        hasPost: Boolean
    ): SceneGuess? = withContext(Dispatchers.Default) {
        val audio = runCatching { audioCandidate(filePath, durationMs, creditsStartMs, hasPost) }.getOrNull()
        val subtitles = runCatching { subtitleCandidate(subtitleUri, durationMs, hasPost) }.getOrNull()
        val guess = SceneDetector.combine(audio, subtitles)
        if (guess != null) prefs.edit().putString(key, SceneGuessCodec.encode(guess)).apply()
        guess
    }

    private suspend fun audioCandidate(path: String, durationMs: Long, creditsStartMs: Long, hasPost: Boolean): SceneCandidate? {
        val lower = path.lowercase()
        if (lower.startsWith("smb") || lower.startsWith("http")) return null
        val windowStart = (creditsStartMs - 30_000L).coerceAtLeast(0L)
        val windowLength = min(durationMs - windowStart, 14 * 60_000L)
        if (windowLength < 60_000L) return null
        val extracted = AutoSyncAudioExtractor.extractWindow(
            appContext, path, null, windowStart, windowLength, targetSampleRate = 4_000
        ) ?: return null
        val envelope = SceneDetector.envelopeOf(extracted.samples, extracted.sampleRate, windowStart)
        return SceneDetector.fromAudio(envelope, creditsStartMs, durationMs, preferLatest = hasPost)
    }

    private fun subtitleCandidate(uri: Uri?, durationMs: Long, hasPost: Boolean): SceneCandidate? {
        uri ?: return null
        val text = when (uri.scheme) {
            "file", null -> java.io.File(uri.path ?: return null).readText(Charsets.UTF_8)
            "content" -> appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
            else -> return null
        }
        val lines = SubtitleCueParser.parse(text).map { SubtitleLine(it.startMs, it.endMs, it.text) }
        return SceneDetector.fromSubtitles(lines, durationMs, hasPost)
    }
}
