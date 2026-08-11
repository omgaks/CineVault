package com.sole.cinevault.subtitles

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.ceil

/**
 * Decodes one local/SAF audio window into 16 kHz mono PCM for Silero VAD.
 *
 * The conversion is deliberately streaming-memory: every MediaCodec output
 * buffer is downmixed and resampled directly into the final target-rate
 * array. Full-rate stereo PCM chunks and a second full-size mono array are
 * never retained. A five-minute 16 kHz window therefore needs about 19.2 MB
 * for sample data instead of keeping well over 100 MB alive at once.
 *
 * SMB is intentionally unsupported here because MediaExtractor cannot consume
 * CineVault's jcifs Media3 DataSource. The caller gates SMB AutoSync.
 */
object AutoSyncAudioExtractor {

    data class ExtractedAudio(val samples: FloatArray, val sampleRate: Int)

    suspend fun extractWindow(
        context: Context,
        filePath: String,
        trackLanguage: String?,
        startMs: Long,
        durationMs: Long,
        targetSampleRate: Int = 16_000
    ): ExtractedAudio? {
        if (durationMs <= 0L || targetSampleRate <= 0) return null

        val extractor = MediaExtractor()
        var assetFileDescriptor: android.content.res.AssetFileDescriptor? = null
        try {
            if (filePath.startsWith("content://", ignoreCase = true)) {
                assetFileDescriptor = context.contentResolver
                    .openAssetFileDescriptor(Uri.parse(filePath), "r") ?: return null
                extractor.setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
            } else {
                extractor.setDataSource(filePath)
            }

            val audioTrackIndex = selectAudioTrackIndex(extractor, trackLanguage) ?: return null
            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null

            val startUs = startMs * 1_000L
            val endUs = startUs + durationMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var sourceSampleRate = inputFormat.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var sourceChannelCount = inputFormat.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var converter: StreamingPcmConverter? = null
            var incompatibleOutput = false

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEnd = false
                var sawOutputEnd = false
                var lastPresentationUs = startUs
                var iterationsWithoutProgress = 0

                while (!sawOutputEnd && lastPresentationUs < endUs) {
                    currentCoroutineContext().ensureActive()

                    if (!sawInputEnd) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleSize = if (inputBuffer != null) {
                                extractor.readSampleData(inputBuffer, 0)
                            } else {
                                -1
                            }
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEnd = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            val newRate = outputFormat.intOrDefault(
                                MediaFormat.KEY_SAMPLE_RATE,
                                sourceSampleRate
                            )
                            val newChannels = outputFormat.intOrDefault(
                                MediaFormat.KEY_CHANNEL_COUNT,
                                sourceChannelCount
                            )
                            val newEncoding = outputFormat.intOrDefault(
                                MediaFormat.KEY_PCM_ENCODING,
                                AudioFormat.ENCODING_PCM_16BIT
                            )

                            // A mid-stream format change would invalidate the
                            // resampling timeline already accumulated. It is
                            // safer to decline AutoSync than analyze corrupt
                            // samples.
                            if (converter != null &&
                                (newRate != sourceSampleRate || newChannels != sourceChannelCount)
                            ) {
                                incompatibleOutput = true
                                sawOutputEnd = true
                            }
                            sourceSampleRate = newRate
                            sourceChannelCount = newChannels
                            pcmEncoding = newEncoding
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            iterationsWithoutProgress++
                            if (iterationsWithoutProgress > 500) sawOutputEnd = true
                        }

                        else -> if (outputIndex >= 0) {
                            iterationsWithoutProgress = 0
                            lastPresentationUs = bufferInfo.presentationTimeUs
                            val outputBuffer = codec.getOutputBuffer(outputIndex)

                            if (outputBuffer != null &&
                                bufferInfo.size > 0 &&
                                bufferInfo.presentationTimeUs >= startUs &&
                                bufferInfo.presentationTimeUs < endUs
                            ) {
                                if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                                    incompatibleOutput = true
                                    sawOutputEnd = true
                                } else {
                                    if (converter == null) {
                                        converter = StreamingPcmConverter(
                                            sourceSampleRate = sourceSampleRate,
                                            channelCount = sourceChannelCount,
                                            targetSampleRate = targetSampleRate,
                                            durationMs = durationMs
                                        )
                                    }
                                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    converter?.consume(outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer())
                                }
                            }

                            codec.releaseOutputBuffer(outputIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEnd = true
                            }
                        }
                    }
                }
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {
                    // A codec that failed during configure/decode may reject stop().
                }
                codec.release()
            }

            if (incompatibleOutput) return null
            val samples = converter?.finish() ?: return null
            if (samples.isEmpty()) return null
            return ExtractedAudio(samples, targetSampleRate)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
            try {
                assetFileDescriptor?.close()
            } catch (_: Exception) {
                // Nothing else can be done during cleanup.
            }
        }
    }

    private fun selectAudioTrackIndex(
        extractor: MediaExtractor,
        preferredLanguage: String?
    ): Int? {
        val audioTracks = (0 until extractor.trackCount).filter { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }
        if (audioTracks.isEmpty()) return null

        if (!preferredLanguage.isNullOrBlank()) {
            audioTracks.firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_LANGUAGE)
                    ?.take(2)
                    ?.equals(preferredLanguage.take(2), ignoreCase = true) == true
            }?.let { return it }
        }
        return audioTracks.first()
    }

    /**
     * Converts interleaved signed 16-bit PCM to mono target-rate floats while
     * each decoder buffer is still small. Linear interpolation is sufficient
     * for VAD, which analyzes speech activity rather than audio fidelity.
     */
    private class StreamingPcmConverter(
        private val sourceSampleRate: Int,
        private val channelCount: Int,
        targetSampleRate: Int,
        durationMs: Long
    ) {
        private val sourceFramesPerOutput = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
        private val maximumOutputSamples = ceil(durationMs * targetSampleRate / 1_000.0)
            .toLong()
            .coerceAtMost(Int.MAX_VALUE.toLong() - 2L)
            .toInt() + 2
        private val output = FloatArray(maximumOutputSamples)

        private var outputSize = 0
        private var sourceFrameIndex = -1L
        private var nextOutputSourcePosition = 0.0
        private var previousMono = 0f
        private var hasPreviousMono = false
        private var pendingChannelCount = 0
        private var pendingChannelSum = 0f

        init {
            require(sourceSampleRate > 0)
            require(channelCount > 0)
            require(targetSampleRate > 0)
        }

        fun consume(buffer: ShortBuffer) {
            while (buffer.hasRemaining() && outputSize < maximumOutputSamples) {
                pendingChannelSum += buffer.get().toFloat()
                pendingChannelCount++
                if (pendingChannelCount == channelCount) {
                    val mono = (pendingChannelSum / channelCount) / 32_768f
                    pendingChannelSum = 0f
                    pendingChannelCount = 0
                    consumeMonoFrame(mono)
                }
            }
        }

        private fun consumeMonoFrame(currentMono: Float) {
            sourceFrameIndex++
            if (!hasPreviousMono) {
                previousMono = currentMono
                hasPreviousMono = true
                appendWhileDue(currentMono, currentMono)
                return
            }

            val previousIndex = sourceFrameIndex - 1L
            while (nextOutputSourcePosition <= sourceFrameIndex.toDouble() &&
                outputSize < maximumOutputSamples
            ) {
                val fraction = (nextOutputSourcePosition - previousIndex)
                    .toFloat()
                    .coerceIn(0f, 1f)
                output[outputSize++] = previousMono + (currentMono - previousMono) * fraction
                nextOutputSourcePosition += sourceFramesPerOutput
            }
            previousMono = currentMono
        }

        private fun appendWhileDue(previous: Float, current: Float) {
            while (nextOutputSourcePosition <= sourceFrameIndex.toDouble() &&
                outputSize < maximumOutputSamples
            ) {
                output[outputSize++] = previous + (current - previous)
                nextOutputSourcePosition += sourceFramesPerOutput
            }
        }

        fun finish(): FloatArray = output.copyOf(outputSize)
    }

    private fun MediaFormat.intOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default
}
