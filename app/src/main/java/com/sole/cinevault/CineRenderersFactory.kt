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

enum class CineAudioRendererPreference {
    PLATFORM_FIRST,
    FFMPEG_FIRST,
}

/**
 * Global renderer preference used only when a player runtime is constructed.
 *
 * Normal playback remains PLATFORM_FIRST. An explicit audio-rescue rebuild can
 * set FFMPEG_FIRST before constructing the replacement runtime, without
 * changing the video decoder selector or video renderer path.
 */
object CineAudioRendererPreferenceHolder {
    @Volatile
    var preference: CineAudioRendererPreference =
        CineAudioRendererPreference.PLATFORM_FIRST
}

/**
 * Maps CineVault's audio preference to Media3's extension renderer mode.
 *
 * Kept pure so the important ordering rule is unit tested independently from
 * Android renderer construction.
 */
fun extensionRendererModeForAudioPreference(
    preference: CineAudioRendererPreference,
): Int = when (preference) {
    CineAudioRendererPreference.PLATFORM_FIRST ->
        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON

    CineAudioRendererPreference.FFMPEG_FIRST ->
        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
}

/**
 * ExoPlayer has no public audio-delay API, so CineVault shifts the audio
 * renderer's reported clock instead. The audio renderer is the playback
 * clock master — offsetting its position shifts video timing relative to
 * audio, which is exactly an A/V sync adjustment.
 *
 * Also registers an FFmpeg-backed audio renderer (media3-ffmpeg-decoder,
 * see build.gradle.kts) as a FALLBACK for codecs the device's own hardware
 * decoder genuinely can't handle at all (DTS/DTS-HD, TrueHD).
 *
 * Also forces PCM decode instead of audio PASSTHROUGH.
 */
@UnstableApi
class CineRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setAudioCapabilities(
                androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
            )
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

        val ffmpegRenderer =
            FfmpegAudioRenderer(eventHandler, eventListener, audioSink)

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            out.add(ffmpegRenderer)
            out.add(platformRenderer)
        } else {
            out.add(platformRenderer)
            out.add(ffmpegRenderer)
        }
    }
}
