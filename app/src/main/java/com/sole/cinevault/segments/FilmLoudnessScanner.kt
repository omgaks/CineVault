package com.sole.cinevault.segments

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sole.cinevault.subtitles.AutoSyncAudioExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * What the player knows about the current film's loudness. Read by the seek bar (real waves),
 * the scene detector and the notice. One film at a time, like the library scroll state.
 */
object FilmLoudness {
    /** Identifies the film the data belongs to (the same seed the seek bar already gets). */
    var seed by mutableStateOf(0)
    /** dB per second; NaN = not measured yet. Replaced (never edited) so Compose sees changes. */
    var db by mutableStateOf<FloatArray?>(null)
    /** Why the audio could not be read, shown in plain words; null = fine or not tried. */
    var failure by mutableStateOf<String?>(null)

    fun reset(newSeed: Int) { seed = newSeed; db = null; failure = null }
}

/**
 * Reads a local film's audio once and keeps one loudness value per second, cached on disk.
 * The last 16 minutes are measured first so the scene detector has what it needs within
 * seconds; the rest fills in behind it. Never throws. Network and streamed films are skipped.
 */
class FilmLoudnessScanner(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "film_loudness").apply { mkdirs() }

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        return File(dir, "$hash.bin")
    }

    private fun cached(key: String, seconds: Int): FloatArray? = runCatching {
        val f = fileFor(key)
        if (!f.exists() || f.length().toInt() != seconds) null else FilmEnvelope.decode(f.readBytes())
    }.getOrNull()

    /** True when the audio can be opened here at all. */
    fun canScan(path: String): Boolean {
        val lower = path.lowercase()
        return !(lower.startsWith("smb") || lower.startsWith("http") || lower.startsWith("rtsp"))
    }

    /**
     * [onUpdate] gets a fresh copy of the whole array after each piece; [onFailure] gets a plain
     * reason if the first piece cannot be read.
     */
    suspend fun scan(
        key: String,
        path: String,
        durationMs: Long,
        onUpdate: (FloatArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!canScan(path) || durationMs < 5 * 60_000L) return
        val seconds = FilmEnvelope.secondsFor(durationMs)
        cached(key, seconds)?.let { onUpdate(it); return }

        scanMutex.withLock {
            withContext(Dispatchers.Default) {
                val db = FloatArray(seconds) { Float.NaN }
                val tailStart = (durationMs - 16 * 60_000L).coerceAtLeast(0L)
                val pieces = ArrayList<LongRange>()
                var t = tailStart
                while (t < durationMs) { pieces.add(t until minOf(t + PIECE_TAIL_MS, durationMs)); t += PIECE_TAIL_MS }
                t = 0L
                while (t < tailStart) { pieces.add(t until minOf(t + PIECE_MS, tailStart)); t += PIECE_MS }

                var failedInARow = 0
                var okPieces = 0
                for (piece in pieces) {
                    currentCoroutineContext().ensureActive()
                    val got = try {
                        AutoSyncAudioExtractor.extractWindow(
                            appContext, path, null, piece.first, piece.last - piece.first + 1, SAMPLE_RATE
                        )
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (got == null) {
                        failedInARow++
                        if (okPieces == 0 && failedInARow >= 1) {
                            onFailure(AutoSyncAudioExtractor.lastFailureReason ?: "the audio track could not be decoded")
                            return@withContext
                        }
                        if (failedInARow >= 3) return@withContext
                        continue
                    }
                    failedInARow = 0
                    okPieces++
                    val env = SceneDetector.envelopeOf(got.samples, got.sampleRate, piece.first, FilmEnvelope.HOP_MS)
                    val firstSec = (piece.first / 1000L).toInt()
                    for (k in env.rms.indices) {
                        val sec = firstSec + k
                        if (sec < seconds) db[sec] = FilmEnvelope.dbOfRms(env.rms[k])
                    }
                    onUpdate(db.copyOf())
                }
                if (db.none { it.isNaN() }) runCatching { fileFor(key).writeBytes(FilmEnvelope.encode(db)) }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 2_000
        const val PIECE_MS = 5 * 60_000L
        const val PIECE_TAIL_MS = 4 * 60_000L
        val scanMutex = Mutex()
    }
}
