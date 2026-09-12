package com.sole.cinevault

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.util.UnstableApi
import com.sole.cinevault.smb.cineVaultMediaSourceFactory

internal data class PlayerRuntime(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
    val decoderSelector: RecoveryAwareMediaCodecSelector,
)

/**
 * Creates the Media3 runtime once for the lifetime of VideoPlayerScreen.
 * Buffering, FFmpeg-extension preference, language selection and audio-focus
 * settings are unchanged from the former inline construction.
 */
@Composable
@OptIn(UnstableApi::class)
internal fun rememberPlayerRuntime(
    context: Context,
    preferredLanguage: String,
    autoEnableEmbeddedSubtitles: Boolean
): PlayerRuntime = remember {
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setPreferredAudioLanguage(preferredLanguage)
            .setPreferredTextLanguage(preferredLanguage)
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(
                C.TRACK_TYPE_TEXT,
                !autoEnableEmbeddedSubtitles
            )
            .build()
    }

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 1_500,
            /* bufferForPlaybackAfterRebufferMs = */ 3_000
        )
        .setBackBuffer(30_000, true)
        .build()

    // Playback Resilience Slice 74:
    // Let Media3 try another MediaCodec decoder when the device's preferred
    // decoder fails to initialise or cannot handle the exact stream profile.
    //
    // This is still the native/hardware tier — it is deliberately separate
    // from the future CPU software-video engine. The existing FFmpeg audio
    // extension remains registered after the platform renderer.
    val decoderSelector = RecoveryAwareMediaCodecSelector()

    val renderersFactory = CineRenderersFactory(context)
        .setExtensionRendererMode(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )
        .setMediaCodecSelector(decoderSelector)
        .setEnableDecoderFallback(true)

    val player = ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(cineVaultMediaSourceFactory(context))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .build()

    PlayerRuntime(
        player = player,
        trackSelector = trackSelector,
        decoderSelector = decoderSelector,
    )
}
