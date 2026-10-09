package com.sole.cinevault.segments

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sole.cinevault.subtitles.AutoSyncAudioExtractor
import com.sole.cinevault.subtitles.SubtitleCueParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
 * What the last detection tried and found, in plain words, so a missing scene time can say why
 * instead of leaving the viewer guessing. Null until a detection has run for this film.
 */
object SceneDetectionStatus {
    var note by mutableStateOf<String?>(null)
}

/** Result of one detection: the scenes found (in film order) and a plain-words explanation. */
class SceneDetectionOutcome(val scenes: List<SceneGuess>, val note: String)

/**
 * Runs the audio and subtitle analysis for the end of a film and combines it with what TMDB
 * says (a mid- and/or post-credit scene exists). Never throws.
 */
class SceneDetectionRunner(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cinevault_scene_guess", Context.MODE_PRIVATE)

    /** A remembered result for this film, or empty. Entries are "start,conf,audio,subs;..." */
    fun cached(key: String): List<SceneGuess> =
        prefs.getString("v2|$key", null)?.split(';')?.mapNotNull { SceneGuessCodec.decode(it) }.orEmpty()

    private fun remember(key: String, scenes: List<SceneGuess>) {
        if (scenes.isNotEmpty()) {
            prefs.edit().putString("v2|$key", scenes.joinToString(";") { SceneGuessCodec.encode(it) }).apply()
        }
    }

    suspend fun detect(
        key: String,
        filePath: String,
        durationMs: Long,
        creditsStartMs: Long,
        subtitleUri: Uri?,
        hasMid: Boolean,
        hasPost: Boolean,
        filmDb: FloatArray?,
        audioFailure: String?
    ): SceneDetectionOutcome = withContext(Dispatchers.Default) {
        var audioNote = ""
        val audio: List<SceneCandidate> = run {
            val result = runCatching { audioCandidates(filePath, durationMs, creditsStartMs, hasPost, hasMid, filmDb) }
            val list = result.getOrNull()
            audioNote = when {
                result.isFailure -> "audio could not be analysed"
                list == null -> audioFailure?.let { "audio not readable ($it)" } ?: "audio not available for this file"
                list.isEmpty() -> "no clear quiet gap in the credits"
                else -> "${list.size} possible moment(s)"
            }
            list.orEmpty()
        }

        var subtitleNote = ""
        val subtitles: List<SceneCandidate> = run {
            val loaded = runCatching { loadSubtitleLines(subtitleUri, filePath) }.getOrNull()
            val list = loaded?.let { SceneDetector.fromSubtitlesAll(it, durationMs, hasMid, hasPost) }.orEmpty()
            subtitleNote = when {
                loaded == null -> "no subtitle file found"
                loaded.size < 20 -> "too few subtitle lines"
                list.isEmpty() -> "no long silence in the dialogue"
                else -> "${list.size} long silence(s)"
            }
            list
        }

        val scenes = SceneDetector.combineAll(audio, subtitles, hasMid, hasPost)
            .filter { it.confidence >= SceneDetector.MIN_SHOWN_CONFIDENCE }
        remember(key, scenes)
        val note = "Audio: $audioNote. Subtitles: $subtitleNote."
        android.util.Log.i("SceneDetect", "path=$filePath credits=$creditsStartMs mid=$hasMid post=$hasPost audio=$audio subs=$subtitles -> $scenes | $note")
        SceneDetectionOutcome(scenes, note)
    }

    /** null = audio could not be read at all; empty = read fine, nothing scene-like. */
    private suspend fun audioCandidates(
        path: String,
        durationMs: Long,
        creditsStartMs: Long,
        hasPost: Boolean,
        hasMid: Boolean,
        filmDb: FloatArray?
    ): List<SceneCandidate>? {
        // Best source: the film's own loudness record, when it covers the end of the film.
        if (filmDb != null) {
            val fromSec = ((creditsStartMs - 60_000L).coerceAtLeast(0L) / 1000L).toInt()
            if (FilmEnvelope.isMeasured(filmDb, fromSec, FilmEnvelope.secondsFor(durationMs))) {
                val env = FilmEnvelope.toAudioEnvelope(filmDb)
                return SceneDetector.fromAudioAll(env, creditsStartMs, durationMs, max = 3)
            }
        }
        val lower = path.lowercase()
        if (lower.startsWith("smb") || lower.startsWith("http")) return null
        val windowStart = (creditsStartMs - 30_000L).coerceAtLeast(0L)
        val windowLength = min(durationMs - windowStart, 14 * 60_000L)
        if (windowLength < 60_000L) return null
        val extracted = AutoSyncAudioExtractor.extractWindow(
            appContext, path, null, windowStart, windowLength, targetSampleRate = 4_000
        ) ?: return null
        val envelope = SceneDetector.envelopeOf(extracted.samples, extracted.sampleRate, windowStart)
        return SceneDetector.fromAudioAll(envelope, creditsStartMs, durationMs, max = 3)
    }

    /** The loaded subtitle, or a subtitle file sitting next to the film; null when there is none. */
    private fun loadSubtitleLines(uri: Uri?, videoPath: String): List<SubtitleLine>? {
        val text = readSubtitleText(uri) ?: sidecarSubtitle(videoPath)?.let { runCatching { it.readText(Charsets.UTF_8) }.getOrNull() }
            ?: return null
        return SubtitleCueParser.parse(text).map { SubtitleLine(it.startMs, it.endMs, it.text) }
    }

    private fun readSubtitleText(uri: Uri?): String? {
        uri ?: return null
        return when (uri.scheme) {
            "file", null -> runCatching { File(uri.path ?: return null).readText(Charsets.UTF_8) }.getOrNull()
            "content" -> runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            else -> null
        }
    }

    private fun sidecarSubtitle(videoPath: String): File? {
        if (videoPath.contains("://")) return null
        val video = File(videoPath)
        val base = video.nameWithoutExtension
        val dir = video.parentFile ?: return null
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(base, ignoreCase = true) && (it.extension.equals("srt", true) || it.extension.equals("vtt", true)) }
            ?.minByOrNull { it.name.length }
    }
}
