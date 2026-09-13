package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DiagnosticsAmber = Color(0xFFFFB547)
private val DiagnosticsGlass = Color(0xE617181D)
private val DiagnosticsBorder = Color(0x33FFFFFF)
private val DiagnosticsPrimary = Color(0xFFF5F5F7)
private val DiagnosticsSecondary = Color(0xA6FFFFFF)

@Composable
fun PlaybackInfoPanel(
    snapshot: PlaybackDiagnosticsSnapshot,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = presentPlaybackDiagnostics(snapshot)
    val liveStatus = buildPlaybackDiagnosticsLiveStatus(snapshot)
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .width(330.dp)
            .background(DiagnosticsGlass, shape)
            .border(1.dp, DiagnosticsBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(
                        DiagnosticsAmber,
                        RoundedCornerShape(4.dp),
                    ),
            )

            Spacer(Modifier.width(9.dp))

            Column {
                Text(
                    text = "PLAYBACK INFO",
                    color = DiagnosticsPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "CineVault diagnostics",
                    color = DiagnosticsSecondary,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            DecoderModeBadge(
                mode = snapshot.decoderMode,
                kind = snapshot.activeDecoderKind,
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = "×",
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = DiagnosticsSecondary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        DiagnosticSection(
            label = "VIDEO",
            value = presentation.videoSummary,
        )

        DiagnosticSection(
            label = "DECODER",
            value = presentation.decoderSummary,
        )

        DiagnosticSection(
            label = "COMPATIBILITY",
            value = presentation.compatibilitySummary
                .removePrefix("Compatibility: "),
        )

        DiagnosticSection(
            label = "READINESS",
            value = liveStatus.readiness,
        )

        DiagnosticSection(
            label = "HEALTH",
            value = liveStatus.health,
        )

        DiagnosticSection(
            label = "SOFTWARE RESCUE",
            value = liveStatus.recovery,
            emphasize = snapshot.fallbackOccurred,
        )

        presentation.fallbackSummary?.let { fallback ->
            DiagnosticSection(
                label = "RECOVERY",
                value = fallback.removePrefix("Fallback: "),
                emphasize = true,
            )
        }
    }
}

@Composable
private fun DecoderModeBadge(
    mode: PlaybackEngineMode,
    kind: ActiveVideoDecoderKind,
) {
    val text = when (kind) {
        ActiveVideoDecoderKind.HARDWARE -> "HW"
        ActiveVideoDecoderKind.SOFTWARE -> "SW"
        ActiveVideoDecoderKind.UNKNOWN -> when (mode) {
            PlaybackEngineMode.HARDWARE -> "HW"
            PlaybackEngineMode.SOFTWARE -> "SW"
        }
    }

    val shape = RoundedCornerShape(999.dp)

    Text(
        text = text,
        modifier = Modifier
            .background(
                DiagnosticsAmber.copy(alpha = 0.12f),
                shape,
            )
            .border(
                1.dp,
                DiagnosticsAmber.copy(alpha = 0.38f),
                shape,
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = DiagnosticsAmber,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
    )
}

@Composable
private fun DiagnosticSection(
    label: String,
    value: String,
    emphasize: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            color = if (emphasize) {
                DiagnosticsAmber
            } else {
                DiagnosticsSecondary
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
        )

        Text(
            text = value,
            color = if (emphasize) {
                DiagnosticsAmber
            } else {
                DiagnosticsPrimary
            },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (emphasize) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
        )
    }
}
