package com.sole.cinevault

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.smb.cineVaultMediaSourceFactory

internal data class PlayerRuntime(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
    val decoderSelector: RecoveryAwareMediaCodecSelector,
)

/**
 * Creates the Media3 runtime once for the lifetime of VideoPlayerScreen.
 *
 * Normal playback uses PLATFORM_FIRST. Audio-rescue runtime construction may
 * request FFMPEG_FIRST; that changes only extension-audio renderer ordering.
 * The video MediaCodec selector and decoder fallback policy remain unchanged.
 */
@Composable
@OptIn(UnstableApi::class)
internal fun rememberPlayerRuntime(
    context: Context,
    preferredLanguage: String,
    autoEnableEmbeddedSubtitles: Boolean,
    audioRendererPreference: CineAudioRendererPreference =
        CineAudioRendererPreference.PLATFORM_FIRST,
): PlayerRuntime = remember(
    context,
    preferredLanguage,
    autoEnableEmbeddedSubtitles,
    audioRendererPreference,
) {
    createPlayerRuntime(
        context = context,
        preferredLanguage = preferredLanguage,
        autoEnableEmbeddedSubtitles = autoEnableEmbeddedSubtitles,
        audioRendererPreference = audioRendererPreference,
    )
}

@OptIn(UnstableApi::class)
internal fun createPlayerRuntime(
    context: Context,
    preferredLanguage: String,
    autoEnableEmbeddedSubtitles: Boolean,
    audioRendererPreference: CineAudioRendererPreference =
        CineAudioRendererPreference.PLATFORM_FIRST,
): PlayerRuntime {
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

    val decoderSelector = RecoveryAwareMediaCodecSelector()

    val renderersFactory = CineRenderersFactory(context)
        .setExtensionRendererMode(
            extensionRendererModeForAudioPreference(audioRendererPreference)
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

    return PlayerRuntime(
        player = player,
        trackSelector = trackSelector,
        decoderSelector = decoderSelector,
    )
}
