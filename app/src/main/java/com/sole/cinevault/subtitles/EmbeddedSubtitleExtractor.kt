package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Which embedded subtitle the player has selected (position among the movie's text tracks). */
data class EmbeddedSubtitleRef(val ordinal: Int, val language: String?) {
    companion object {
        /** Reads "embedded:<group>:<track>" keys; the group index is the text-track position. */
        fun fromSelectedKey(key: String?, language: String?): EmbeddedSubtitleRef? {
            if (key == null || !key.startsWith("embedded:")) return null
            val group = key.removePrefix("embedded:").substringBefore(':').toIntOrNull() ?: return null
            return EmbeddedSubtitleRef(group, language)
        }
    }
}

/** One text track found inside a movie file. */
data class EmbeddedSubtitleTrack(
    val language: String?,
    val label: String?,
    val cues: List<RawCue>,
    /** False for image subtitles (PGS, VobSub): kept in the list only so positions line up. */
    val supported: Boolean = true,
)

/**
 * Reads text subtitles that are stored INSIDE a movie (MKV / WebM / MP4) and saves one of them
 * as a normal SRT file, so AI Translate and Dual subtitles can use it like any other subtitle.
 *
 * Uses Media3's own container parsers, so the timing matches exactly what the player shows.
 * The movie is read once from start to finish (a few seconds for a typical film on phone
 * storage); the result is cached, so the second request is instant.
 *
 * Supported: SubRip, ASS/SSA (styling is stripped to plain text), WebVTT, and MP4 tx3g.
 * Image subtitles (PGS, VobSub) can't be turned into text without OCR and are reported as such.
 */
@OptIn(UnstableApi::class)
object EmbeddedSubtitleExtractor {

    sealed class Result {
        data class Success(val file: File, val language: String?, val cueCount: Int) : Result()
        data class Failure(val reason: String) : Result()
    }

    suspend fun extract(
        context: Context,
        videoPath: String,
        ref: EmbeddedSubtitleRef,
        onProgress: (Int) -> Unit = {},
    ): Result {
        val cacheFile = cacheFileFor(context, videoPath, ref)
        if (cacheFile.isFile && cacheFile.length() > 0L) {
            val count = cacheFile.readText().split("\n\n").count { it.isNotBlank() }
            return Result.Success(cacheFile, ref.language, count)
        }

        val tracks = try {
            readTextTracks(context, videoPath, onProgress)
        } catch (e: UnsupportedSourceException) {
            return Result.Failure(e.message ?: "This video can't be read for embedded subtitles.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.Failure("Couldn't read the embedded subtitles (${e.javaClass.simpleName}).")
        }

        if (tracks.isEmpty()) {
            return Result.Failure("No text subtitles were found inside this movie (image subtitles such as PGS can't be translated).")
        }
        val chosen = chooseTrack(tracks, ref)
            ?: return Result.Failure("Couldn't match the selected subtitle to a text track in the file.")
        if (!chosen.supported) {
            return Result.Failure("The selected subtitle is a picture-based track (such as PGS), which can't be read as text. Pick a text subtitle track instead.")
        }
        if (chosen.cues.isEmpty()) {
            return Result.Failure("The selected embedded subtitle has no text cues.")
        }
        cacheFile.writeText(toSrt(chosen.cues))
        return Result.Success(cacheFile, chosen.language ?: ref.language, chosen.cues.size)
    }

    /** Where the extracted copy of this embedded subtitle is (or will be) saved. */
    fun cacheFileFor(context: Context, videoPath: String, ref: EmbeddedSubtitleRef): File =
        File(File(context.filesDir, "embedded_subs").apply { mkdirs() }, cacheFileName(videoPath, ref))

    /**
     * Forgets the saved copy, so the next request reads the subtitle out of the movie again
     * (and shows the "Reading subtitle · N%" progress). Returns true if a copy was removed.
     */
    fun clearCache(context: Context, videoPath: String, ref: EmbeddedSubtitleRef): Boolean {
        val file = cacheFileFor(context, videoPath, ref)
        return file.isFile && file.delete()
    }

    internal fun cacheFileName(videoPath: String, ref: EmbeddedSubtitleRef): String {
        val base = videoPath.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9 ._()\\[\\]-]"), "_").take(80).ifBlank { "movie" }
        return "$base-embedded-${ref.ordinal}-${(ref.language ?: "und").take(8)}-${videoPath.hashCode().toUInt().toString(16)}.srt"
    }

    // ── Pure helpers (unit-tested) ───────────────────────────────────────────────

    /** Picks the track for [ref]: same position when the language agrees, else same language. */
    internal fun chooseTrack(tracks: List<EmbeddedSubtitleTrack>, ref: EmbeddedSubtitleRef): EmbeddedSubtitleTrack? {
        fun sameLanguage(a: String?, b: String?): Boolean {
            if (a.isNullOrBlank() || b.isNullOrBlank()) return false
            return a.take(2).equals(b.take(2), ignoreCase = true)
        }
        val byPosition = tracks.getOrNull(ref.ordinal)
        if (byPosition != null && (ref.language.isNullOrBlank() || byPosition.language.isNullOrBlank() ||
                sameLanguage(byPosition.language, ref.language))
        ) return byPosition
        val sameLang = tracks.filter { sameLanguage(it.language, ref.language) }
        if (sameLang.isNotEmpty()) return sameLang.first()
        return byPosition ?: tracks.firstOrNull()
    }

    internal fun toSrt(cues: List<RawCue>): String {
        val sb = StringBuilder()
        cues.sortedBy { it.startMs }.forEachIndexed { i, cue ->
            sb.append(i + 1).append('\n')
            sb.append(timecode(cue.startMs)).append(" --> ").append(timecode(cue.endMs)).append('\n')
            sb.append(cue.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    internal fun timecode(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, t % 1000)
    }

    /** Matroska SubRip sample: "1\n00:00:00,000 --> <duration>\n<text>". */
    internal fun parseSubripSample(startUs: Long, data: ByteArray): RawCue? {
        val text = String(data, Charsets.UTF_8)
        val arrow = text.indexOf("-->")
        if (arrow < 0) return null
        val lineEnd = text.indexOf('\n', arrow)
        if (lineEnd < 0) return null
        val duration = parseClock(text.substring(arrow + 3, lineEnd).trim()) ?: return null
        val body = text.substring(lineEnd + 1).trim()
        if (body.isEmpty()) return null
        val startMs = startUs / 1000
        return RawCue(startMs, startMs + duration.coerceAtLeast(1L), body)
    }

    /** Matroska WebVTT sample: "WEBVTT\n\n00:00:00.000 --> <duration>\n<text>". */
    internal fun parseVttSample(startUs: Long, data: ByteArray): RawCue? = parseSubripSample(startUs, data)

    /** Matroska ASS sample: "Dialogue: 0:00:00:00,<h:mm:ss:cc>,ReadOrder,Layer,Style,Name,ML,MR,MV,Effect,Text". */
    internal fun parseAssSample(startUs: Long, data: ByteArray): RawCue? {
        val text = String(data, Charsets.UTF_8)
        if (!text.startsWith("Dialogue:")) return null
        val afterLabel = text.removePrefix("Dialogue:").trimStart()
        val parts = afterLabel.split(',', limit = 11)
        // 0 = placeholder start, 1 = duration, 2..9 = ReadOrder..Effect, 10 = Text
        if (parts.size < 11) return null
        val duration = parseAssClock(parts[1]) ?: return null
        val body = cleanAssText(parts[10])
        if (body.isEmpty()) return null
        val startMs = startUs / 1000
        return RawCue(startMs, startMs + duration.coerceAtLeast(1L), body)
    }

    internal fun cleanAssText(raw: String): String {
        if (raw.contains("\\p1") || raw.contains("\\p2")) return "" // vector drawing, not text
        return raw
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
            .lines().joinToString("\n") { it.trim() }.trim()
    }

    /** MP4 tx3g sample: 2-byte big-endian length, then UTF-8 text. */
    internal fun parseTx3gText(data: ByteArray): String {
        if (data.size < 2) return ""
        val len = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val end = minOf(data.size, 2 + len)
        return String(data, 2, (end - 2).coerceAtLeast(0), Charsets.UTF_8).trim()
    }

    private fun parseClock(s: String): Long? {
        val m = Regex("""(\d+):(\d{2}):(\d{2})[,.](\d{1,3})""").matchEntire(s) ?: return null
        val (h, mi, se, ms) = m.destructured
        return ((h.toLong() * 60 + mi.toLong()) * 60 + se.toLong()) * 1000 + ms.padEnd(3, '0').toLong()
    }

    private fun parseAssClock(s: String): Long? {
        val m = Regex("""(\d+):(\d{2}):(\d{2}):(\d{2})""").matchEntire(s.trim()) ?: return null
        val (h, mi, se, cs) = m.destructured
        return ((h.toLong() * 60 + mi.toLong()) * 60 + se.toLong()) * 1000 + cs.toLong() * 10
    }

    // ── Container reading ────────────────────────────────────────────────────────

    private class UnsupportedSourceException(message: String) : Exception(message)

    private class RawSample(val timeUs: Long, val data: ByteArray)

    private class TextSink : TrackOutput {
        var format: Format? = null
        val samples = ArrayList<RawSample>()
        private var pending = ByteArrayOutputStream()
        private val scratch = ByteArray(8192)

        override fun format(format: Format) { this.format = format }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val n = input.read(scratch, 0, minOf(length, scratch.size))
            if (n == C.RESULT_END_OF_INPUT) return n
            pending.write(scratch, 0, n)
            return n
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            pending.write(data.data, data.position, length)
            data.skipBytes(length)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            val all = pending.toByteArray()
            val start = (all.size - offset - size).coerceAtLeast(0)
            val end = (start + size).coerceAtMost(all.size)
            samples.add(RawSample(timeUs, all.copyOfRange(start, end)))
            pending = ByteArrayOutputStream().also { if (offset > 0) it.write(all, all.size - offset, offset) }
        }
    }

    /** Swallows audio/video bytes quickly: they are not needed. */
    private class DiscardSink : TrackOutput {
        private val scratch = ByteArray(32 * 1024)
        override fun format(format: Format) {}
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            input.read(scratch, 0, minOf(length, scratch.size))
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) { data.skipBytes(length) }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {}
    }

    private class ChannelReader(private val channel: FileChannel) : DataReader {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = channel.read(ByteBuffer.wrap(buffer, offset, length))
            return if (n < 0) C.RESULT_END_OF_INPUT else n
        }
    }

    private suspend fun readTextTracks(
        context: Context,
        videoPath: String,
        onProgress: (Int) -> Unit,
    ): List<EmbeddedSubtitleTrack> {
        var pfd: android.os.ParcelFileDescriptor? = null
        var raf: RandomAccessFile? = null
        val channel: FileChannel = try {
            when {
                videoPath.startsWith("content://", ignoreCase = true) -> {
                    pfd = context.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")
                        ?: throw UnsupportedSourceException("Couldn't open the video file.")
                    FileInputStream(pfd.fileDescriptor).channel
                }
                videoPath.contains("://") ->
                    throw UnsupportedSourceException("Embedded subtitles can't be read from streamed videos yet.")
                else -> {
                    val file = File(videoPath)
                    if (!file.isFile) throw UnsupportedSourceException("The video file couldn't be found.")
                    raf = RandomAccessFile(file, "r")
                    raf.channel
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            throw UnsupportedSourceException("The video file couldn't be opened.")
        }

        try {
            val length = channel.size()
            val head = ByteBuffer.allocate(16)
            channel.position(0)
            channel.read(head)
            val magic = head.array()
            val isMatroska = magic[0] == 0x1A.toByte() && magic[1] == 0x45.toByte() &&
                magic[2] == 0xDF.toByte() && magic[3] == 0xA3.toByte()
            val isMp4 = String(magic, 4, 4, Charsets.ISO_8859_1) in setOf("ftyp", "moov", "mdat", "free", "wide")
            val extractor: Extractor = when {
                isMatroska -> MatroskaExtractor()
                isMp4 -> Mp4Extractor()
                else -> throw UnsupportedSourceException("Embedded subtitles can only be read from MKV, WebM and MP4 files.")
            }

            val sinks = LinkedHashMap<Int, Any>() // track id -> TextSink | DiscardSink
            val output = object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput {
                    val sink: Any = if (type == C.TRACK_TYPE_TEXT) TextSink() else DiscardSink()
                    sinks[id] = sink
                    return sink as TrackOutput
                }
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {}
            }
            extractor.init(output)

            var input = DefaultExtractorInput(ChannelReader(channel.position(0)), 0L, length)
            val positionHolder = PositionHolder()
            var lastPercent = -1
            while (true) {
                currentCoroutineContext().ensureActive()
                val result = extractor.read(input, positionHolder)
                if (result == Extractor.RESULT_END_OF_INPUT) break
                if (result == Extractor.RESULT_SEEK) {
                    channel.position(positionHolder.position)
                    input = DefaultExtractorInput(ChannelReader(channel), positionHolder.position, length)
                }
                if (length > 0) {
                    val pct = (channel.position() * 100 / length).toInt().coerceIn(0, 99)
                    if (pct != lastPercent) { lastPercent = pct; onProgress(pct) }
                }
            }
            onProgress(100)

            return sinks.values.filterIsInstance<TextSink>().mapNotNull { sink ->
                val format = sink.format ?: return@mapNotNull null
                val cues = convert(format.sampleMimeType, sink.samples)
                // Image tracks are KEPT (as unsupported) so the Nth subtitle the player lists is
                // still the Nth entry here; dropping them shifted every later track by one.
                EmbeddedSubtitleTrack(format.language, format.label, cues ?: emptyList(), supported = cues != null)
            }
        } finally {
            try { raf?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun convert(mime: String?, samples: List<RawSample>): List<RawCue>? = when (mime) {
        MimeTypes.APPLICATION_SUBRIP -> samples.mapNotNull { parseSubripSample(it.timeUs, it.data) }
        MimeTypes.TEXT_SSA -> samples.mapNotNull { parseAssSample(it.timeUs, it.data) }
        MimeTypes.TEXT_VTT -> samples.mapNotNull { parseVttSample(it.timeUs, it.data) }
        MimeTypes.APPLICATION_TX3G -> {
            val cues = ArrayList<RawCue>()
            val sorted = samples.sortedBy { it.timeUs }
            sorted.forEachIndexed { i, s ->
                val text = parseTx3gText(s.data)
                if (text.isNotEmpty()) {
                    val start = s.timeUs / 1000
                    val end = sorted.getOrNull(i + 1)?.timeUs?.div(1000) ?: (start + 3000)
                    cues.add(RawCue(start, maxOf(end, start + 1), text))
                }
            }
            cues
        }
        else -> null // image or unsupported subtitle types
    }
}
