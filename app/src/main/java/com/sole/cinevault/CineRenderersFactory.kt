package com.sole.cinevault

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Global audio/video sync offset in microseconds.
 * Positive = audio plays later relative to video; negative = earlier.
 * Written by the player UI (+/- ms steppers), read by the audio renderer below.
 */
object AudioSyncHolder {
    @Volatile
    var offsetUs: Long = 0L
}

/**
 * ExoPlayer has no public audio-delay API, so CineVault shifts the audio
 * renderer's reported clock instead. The audio renderer is the playback
 * clock master — offsetting its position shifts video timing relative to
 * audio, which is exactly an A/V sync adjustment.
 *
 * Also registers an FFmpeg-backed audio renderer (media3-ffmpeg-decoder,
 * see build.gradle.kts) as a FALLBACK for codecs the device's own hardware
 * decoder genuinely can't handle at all (DTS/DTS-HD, TrueHD). See the
 * ordering note on buildAudioRenderers below for why this doesn't affect
 * formats the device already decodes natively.
 *
 * Also forces PCM decode instead of audio PASSTHROUGH (see buildAudioSink
 * below) — a separate, well-known ExoPlayer issue from the codec-support
 * one above: some devices report that they can play compressed AC3/DD5.1
 * as a raw "passthrough" bitstream (meant for a connected AV receiver that
 * decodes it itself) even when the output is the phone's own built-in
 * speaker, which obviously can't decode a compressed bitstream directly.
 * ExoPlayer trusts that claim and sends raw undecoded audio to the
 * speaker — video and subtitles keep working fine (they're unrelated
 * pipelines), and no error is thrown anywhere, since nothing actually
 * failed from the app's point of view. It just produces silence. Forcing
 * PCM-only capabilities means the platform decoder always decodes AC3
 * itself before handing audio to the speaker, which is the correct
 * behavior for a phone with no AV receiver attached anyway.
 */
@UnstableApi
class CineRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            // Forces every compressed format (AC3/DD5.1 included) through
            // real decoding to PCM rather than being handed to the audio
            // output as a raw passthrough bitstream.
            .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        val platformRenderer = object : MediaCodecAudioRenderer(
            context,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            audioSink
        ) {
            override fun getPositionUs(): Long {
                return super.getPositionUs() + AudioSyncHolder.offsetUs
            }
        }

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            out.add(platformRenderer)
            return
        }

        val ffmpegRenderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            // Not what CineVault actually uses (see VideoPlayerScreen.kt),
            // but handled correctly in case that ever changes: FFmpeg tried
            // first, platform decoder as the fallback.
            out.add(ffmpegRenderer)
            out.add(platformRenderer)
        } else {
            // ON — the mode CineVault sets. Hardware decoder tried first;
            // FFmpeg only engages when the device has no decoder at all
            // for that particular codec.
            out.add(platformRenderer)
            out.add(ffmpegRenderer)
        }
    }
}
