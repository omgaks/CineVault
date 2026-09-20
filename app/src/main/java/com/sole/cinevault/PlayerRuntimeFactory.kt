package com.sole.cinevault

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
@OptIn(UnstableApi::class)
internal fun rememberPlayerRuntime(
    context: Context,
    preferredLanguage: String,
    autoEnableEmbeddedSubtitles: Boolean,
    currentVideoPath: String,
    audioRendererPreference: CineAudioRendererPreference =
        AudioRuntimeRescueController.rendererPreference,
): PlayerRuntime {
    // G7A-1: subtitle-behaviour toggles must NOT recreate ExoPlayer.
    // Recreating the runtime while the player screen is active leaves the
    // surrounding subtitle/player effects briefly holding different player
    // instances, which can pause/crash the session. Runtime identity remains
    // tied only to things that genuinely require a new player.
    val runtime = remember(
        context,
        preferredLanguage,
        currentVideoPath,
        audioRendererPreference,
    ) {
        createPlayerRuntime(
            context = context,
            preferredLanguage = preferredLanguage,
            autoEnableEmbeddedSubtitles = autoEnableEmbeddedSubtitles,
            audioRendererPreference = audioRendererPreference,
        ).also { created ->
            AudioRuntimeRescueController.consumePendingPlan(currentVideoPath)
                ?.toHandover()
                ?.let { handover ->
                    applyAudioRuntimeRescueHandover(
                        player = created.player,
                        trackSelector = created.trackSelector,
                        handover = handover,
                    )
                }
        }
    }

    // Apply the embedded-subtitle preference live to the existing selector.
    // This changes text-track eligibility without interrupting playback.
    LaunchedEffect(runtime.trackSelector, autoEnableEmbeddedSubtitles) {
        runtime.trackSelector.parameters =
            runtime.trackSelector.buildUponParameters()
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_TEXT,
                    !autoEnableEmbeddedSubtitles,
                )
                .build()
    }

    return runtime
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
            15_000,
            50_000,
            1_500,
            3_000
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
            true
        )
        .build()

    return PlayerRuntime(
        player = player,
        trackSelector = trackSelector,
        decoderSelector = decoderSelector,
    )
}
