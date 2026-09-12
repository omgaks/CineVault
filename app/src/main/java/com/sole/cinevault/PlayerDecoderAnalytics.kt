package com.sole.cinevault

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerDecoderAnalytics(
    player: ExoPlayer,
    capabilityReport: VideoDecoderCapabilityReport?,
    engineMode: PlaybackEngineMode,
    onDecoderStatusChanged: (ActiveVideoDecoderStatus) -> Unit,
) {
    DisposableEffect(player, capabilityReport, engineMode) {
        onDecoderStatusChanged(ActiveVideoDecoderStatus())

        val listener = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                onDecoderStatusChanged(
                    ActiveVideoDecoderStatus(
                        kind = classifyActiveVideoDecoder(
                            decoderName = decoderName,
                            capabilityReport = capabilityReport,
                            engineMode = engineMode,
                        ),
                        decoderName = decoderName,
                    )
                )
            }
        }

        player.addAnalyticsListener(listener)

        onDispose {
            player.removeAnalyticsListener(listener)
        }
    }
}
