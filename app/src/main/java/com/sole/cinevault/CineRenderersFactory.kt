package com.sole.cinevault

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
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
        out.add(
            object : MediaCodecAudioRenderer(
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
        )
    }
}
