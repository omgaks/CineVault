package com.sole.cinevault.subtitles

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

// ── Auto-Sync: audio extraction ──────────────────────────────────────────
// Decodes a WINDOW of the video's audio (not the whole file) into 16kHz
// mono float PCM in [-1, 1], suitable for sherpa-onnx's Silero VAD.
//
// LOCAL FILES / SAF (content://) ONLY for this first version — SMB network
// shares play through CineVault's own jcifs-ng-backed Media3 DataSource,
// which android.media.MediaExtractor has no way to read from directly.
// Supporting SMB here would mean tapping ExoPlayer's own decode pipeline
// instead (a genuinely separate, bigger piece of work) rather than the
// simpler direct MediaExtractor+MediaCodec approach below. Gated off for
// smb:// paths at the call site in AutoSyncEngine.kt rather than failing
// silently.
object AutoSyncAudioExtractor {

    data class ExtractedAudio(val samples: FloatArray, val sampleRate: Int)

    // trackLanguage: the language of the audio track ExoPlayer currently
    // has SELECTED for playback — extraction must analyze the SAME track
    // the person is actually listening to, not just "the first audio
    // track", since a subtitle can be correct for the main audio and wrong
    // for a commentary track (per the spec's audio-track-selection point).
    //
    // FIX: now takes Context and actually handles content:// URIs. This
    // previously always called extractor.setDataSource(filePath) with the
    // raw path string regardless of scheme — which silently fails (or
    // throws) for SAF content:// URIs, since MediaExtractor needs an
    // actual file descriptor for those, not a path string. The interface
    // already implied content:// support (AutoSyncEngine.kt explicitly
    // checked for it); this makes the implementation actually match.
    // FIX: now suspend and cancellation-aware — the decode loop below can
    // run for real seconds on a multi-minute audio window, and previously
    // had no way to notice if the surrounding coroutine was cancelled
    // (video changed, screen closed mid-analysis), so it would keep
    // decoding to completion regardless. ensureActive() calls inside the
    // loop below make it stop promptly instead.
    suspend fun extractWindow(
        context: Context,
        filePath: String,
        trackLanguage: String?,
        startMs: Long,
        durationMs: Long,
        targetSampleRate: Int = 16000
    ): ExtractedAudio? {
        val extractor = MediaExtractor()
        var afd: android.content.res.AssetFileDescriptor? = null
        try {
            if (filePath.startsWith("content://", ignoreCase = true)) {
                afd = context.contentResolver.openAssetFileDescriptor(Uri.parse(filePath), "r") ?: return null
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } else {
                extractor.setDataSource(filePath)
            }

            val audioTrackIndex = selectAudioTrackIndex(extractor, trackLanguage) ?: return null
            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val startUs = startMs * 1000L
            val endUs = startUs + durationMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var sourceChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
            val pcmChunks = mutableListOf<ShortArray>()

            // FIX: codec.stop()/release() previously only ran on the
            // success path at the end of the decode loop — any exception
            // thrown mid-decode (corrupt stream, codec error, etc.) skipped
            // both calls entirely and leaked the native MediaCodec
            // instance. Now in a finally scoped specifically to the
            // codec's own lifetime, so every exit path releases it.
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false
                var lastPresentationUs = startUs
                var iterationsWithoutProgress = 0

                while (!sawOutputEOS && lastPresentationUs < endUs) {
                    currentCoroutineContext().ensureActive()
                    if (!sawInputEOS) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIndex)
                            val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIndex >= 0 -> {
                            iterationsWithoutProgress = 0
                            lastPresentationUs = bufferInfo.presentationTimeUs
                            val outputBuffer = codec.getOutputBuffer(outIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val shortBuf = outputBuffer.asShortBuffer()
                                val chunk = ShortArray(shortBuf.remaining())
                                shortBuf.get(chunk)
                                pcmChunks.add(chunk)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sourceSampleRate)
                            sourceChannelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, sourceChannelCount)
                        }
                        else -> {
                            // Neither an input nor output buffer was ready
                            // this pass — normal occasionally, but if it
                            // happens for too many consecutive iterations
                            // something's stuck (corrupt/truncated stream)
                            // rather than just slow, and this loop should
                            // bail instead of spinning forever on a tablet
                            // with no way to force-kill it.
                            iterationsWithoutProgress++
                            if (iterationsWithoutProgress > 500) { sawOutputEOS = true }
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                codec.release()
            }

            val mono = downmixToMono(pcmChunks, sourceChannelCount)
            // FIX: pcmChunks (the raw decoded PCM — often the single
            // biggest buffer here, since it's still full stereo/full
            // sample-rate before downmixing) was staying reachable for the
            // rest of this function purely because the variable was still
            // in scope, even though nothing after this point reads it
            // again. On a long window that's the difference between 2 and
            // 3 large buffers alive at once during resampleLinear() below
            // — clearing it here lets the GC reclaim it before that next
            // allocation instead of after, which is what was pushing this
            // over the heap ceiling during longer Auto-Sync windows.
            pcmChunks.clear()
            val resampled = resampleLinear(mono, sourceSampleRate, targetSampleRate)
            return ExtractedAudio(resampled, targetSampleRate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } finally {
            extractor.release()
            try { afd?.close() } catch (_: Exception) {}
        }
    }

    private fun selectAudioTrackIndex(extractor: MediaExtractor, preferredLanguage: String?): Int? {
        val audioTracks = (0 until extractor.trackCount).filter { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        if (audioTracks.isEmpty()) return null
        if (preferredLanguage != null) {
            audioTracks.firstOrNull { i ->
                val lang = extractor.getTrackFormat(i).getString(MediaFormat.KEY_LANGUAGE)
                lang != null && lang.take(2).equals(preferredLanguage.take(2), ignoreCase = true)
            }?.let { return it }
        }
        return audioTracks.first()
    }

    private fun downmixToMono(chunks: List<ShortArray>, channelCount: Int): FloatArray {
        if (channelCount <= 1) {
            val total = chunks.sumOf { it.size }
            val out = FloatArray(total)
            var pos = 0
            for (chunk in chunks) {
                for (s in chunk) { out[pos] = s / 32768f; pos++ }
            }
            return out
        }
        val totalFrames = chunks.sumOf { it.size / channelCount }
        val out = FloatArray(totalFrames)
        var frameIdx = 0
        for (chunk in chunks) {
            var i = 0
            while (i + channelCount <= chunk.size) {
                var sum = 0f
                for (c in 0 until channelCount) sum += chunk[i + c]
                out[frameIdx] = (sum / channelCount) / 32768f
                frameIdx++
                i += channelCount
            }
        }
        return out
    }

    // Simple linear-interpolation resampler — not audiophile quality, but
    // more than sufficient for VAD, which only cares about coarse energy/
    // speech-envelope shape, not fidelity.
    private fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = toRate.toDouble() / fromRate.toDouble()
        val outLength = (input.size * ratio).toInt()
        val out = FloatArray(outLength)
        for (i in out.indices) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = input.getOrElse(idx) { input.last() }
            val b = input.getOrElse(idx + 1) { input.last() }
            out[i] = a + (b - a) * frac
        }
        return out
    }
}
