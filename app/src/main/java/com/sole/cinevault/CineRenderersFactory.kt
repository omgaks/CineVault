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
 * see build.gradle.kts) as a FALLBACK — the device's own hardware decoder
 * often can't handle DTS/DTS-HD, TrueHD, or some E-AC3 variants, which
 * previously meant those files played with picture but total silence,
 * since this factory only ever registered the platform decoder with
 * nothing to fall through to.
 *
 * Ordering matters here: with extensionRendererMode == ON (what
 * VideoPlayerScreen.kt actually sets), the platform/hardware renderer is
 * added FIRST and FFmpeg second — ExoPlayer tries renderers in list order
 * per track and only moves to the next one if the current one can't
 * handle the format, so anything the device already decodes natively
 * (AAC, standard AC3, etc.) keeps using that faster, more power-efficient
 * hardware path untouched. FFmpeg only actually gets used for the codecs
 * the platform genuinely has no decoder for.
 *
 * Known limitation: the sync-offset override below only wraps the
 * platform MediaCodecAudioRenderer. If a file falls through to the
 * FFmpeg renderer, the audio delay slider in the player UI currently has
 * no effect on it — that renderer doesn't get the same getPositionUs()
 * override, since it's a separate class from a different library.
 */
@UnstableApi
class CineRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
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
