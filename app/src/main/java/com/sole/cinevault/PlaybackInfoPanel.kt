package com.sole.cinevault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DiagnosticsMint = Color(0xFF7CE0C3)
private val DiagnosticsSky = Color(0xFF6FC3FF)
private val DiagnosticsViolet = Color(0xFFC792FF)
private val DiagnosticsRose = Color(0xFFFF6E8C)
private val DiagnosticsAmber = Color(0xFFFFB547)

/** Each report line gets its own neon colour, matching the pill colour code. */
private fun diagnosticLabelColor(label: String): Color = when {
    label.contains("AUDIO") -> DiagnosticsSky
    label.contains("VIDEO") || label.contains("CODEC") || label.contains("PIPELINE") -> DiagnosticsMint
    label.contains("FAIL") || label.contains("RECOVER") || label.contains("RESCUE") -> DiagnosticsRose
    label.contains("COMPAT") || label.contains("READINESS") || label.contains("HEALTH") -> DiagnosticsViolet
    else -> DiagnosticsAmber
}
private val DiagnosticsGlass = Color(0xE617181D)
private val DiagnosticsBorder = Color(0x33FFFFFF)
private val DiagnosticsPrimary = Color(0xFFF5F5F7)
private val DiagnosticsSecondary = Color(0xA6FFFFFF)

@Composable
fun PlaybackInfoPanel(
    snapshot: PlaybackDiagnosticsSnapshot,
    onDismiss: () -> Unit,
    onShowCompatibilityReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val presentation = presentPlaybackDiagnostics(snapshot)
    val liveStatus = buildPlaybackDiagnosticsLiveStatus(snapshot)
    val failureHistoryPresentation =
        presentPlaybackFailureHistory(snapshot.failureHistory)
    val audioResilience = assessAudioPlaybackResilience(
        selectedAudio = snapshot.audioMimeType?.let {
            PlaybackStreamDescriptor(
                kind = PlaybackStreamKind.AUDIO,
                mimeType = snapshot.audioMimeType,
                codecString = snapshot.audioCodecString,
                language = snapshot.audioLanguage,
                selected = true,
            )
        },
        activeDecoder = ActiveAudioDecoderStatus(
            kind = snapshot.activeAudioDecoderKind,
            decoderName = snapshot.audioDecoderName,
        ),
    )
    val sections = buildList<Triple<String, String, Boolean>> {
        add(Triple("VIDEO", presentation.videoSummary, false))
        presentation.codecDetailsSummary?.let { add(Triple("CODEC DETAILS", it, false)) }
        add(Triple("VIDEO DECODER", presentation.decoderSummary, false))
        presentation.audioSummary?.let { add(Triple("AUDIO", it, false)) }
        presentation.audioDecoderSummary?.let {
            add(Triple("AUDIO DECODER", it, snapshot.activeAudioDecoderKind == ActiveAudioDecoderKind.FFMPEG))
        }
        presentation.audioResilienceSummary?.let {
            add(
                Triple(
                    "AUDIO RESILIENCE",
                    it,
                    audioResilience.readiness == AudioPlaybackReadiness.FFMPEG_RESCUED ||
                        audioResilience.readiness == AudioPlaybackReadiness.FFMPEG_RESCUE_EXPECTED,
                )
            )
        }
        audioFfmpegRescueOutcomeLabel(snapshot.audioFfmpegRescueOutcome)?.let {
            add(Triple("FFMPEG RESCUE", it, snapshot.audioFfmpegRescueOutcome != AudioFfmpegRescueOutcome.NOT_ATTEMPTED))
        }
        presentation.pipelineSummary?.let {
            add(Triple("PIPELINE", it, snapshot.activeAudioDecoderKind == ActiveAudioDecoderKind.FFMPEG))
        }
        snapshot.lastFailureDiagnostic?.let { failure ->
            add(
                Triple(
                    if (failure.severity == PlaybackFailureSeverity.TERMINAL) "LAST FAILURE" else "LAST RECOVERY",
                    playbackFailureDiagnosticSummary(failure),
                    true,
                )
            )
        }
        failureHistoryPresentation?.let { history ->
            add(
                Triple(
                    "RECOVERY HISTORY",
                    buildString {
                        append(history.summary)
                        history.latestEvents.forEach { event ->
                            append("\n")
                            append(event)
                        }
                    },
                    history.hasTerminalFailure,
                )
            )
        }
        add(Triple("COMPATIBILITY", presentation.compatibilitySummary.removePrefix("Compatibility: "), false))
        add(Triple("READINESS", liveStatus.readiness, false))
        add(Triple("HEALTH", liveStatus.health, false))
        add(Triple("SOFTWARE RESCUE", liveStatus.recovery, snapshot.fallbackOccurred))
        presentation.fallbackSummary?.let { add(Triple("RECOVERY", it.removePrefix("Fallback: "), true)) }
    }
    val reportText = buildString {
        append("CineVault playback report\n")
        sections.forEach { (label, value, _) ->
            append("\n")
            append(label)
            append(": ")
            append(value)
        }
    }
    val shape = androidx.compose.foundation.shape.CutCornerShape(12.dp)
    val edge = when {
        snapshot.activeDecoderKind == ActiveVideoDecoderKind.SOFTWARE -> DiagnosticsRose
        snapshot.fallbackOccurred -> DiagnosticsAmber
        else -> DiagnosticsMint
    }

    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 330.dp)
            .lumenFrame(edge)
            .verticalScroll(rememberScrollState())
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
                    .background(edge, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = "PLAYBACK INFO",
                    color = edge,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "PLAYBACK REPORT · TAP PILL TO CLOSE",
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

        sections.forEach { (label, value, emphasize) ->
            DiagnosticSection(label, value, emphasize)
        }

        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "SHARE  ↗",
            modifier = Modifier
                .weight(1f)
                .background(DiagnosticsSky.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .border(1.dp, DiagnosticsSky.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .clickable {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(send, "Share playback report")
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            color = DiagnosticsSky,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = "COPY REPORT  ⧉",
            modifier = Modifier
                .weight(1f)
                .background(
                    DiagnosticsAmber.copy(alpha = 0.10f),
                    RoundedCornerShape(10.dp),
                )
                .border(
                    1.dp,
                    DiagnosticsAmber.copy(alpha = 0.30f),
                    RoundedCornerShape(10.dp),
                )
                .clickable {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("CineVault playback report", reportText)
                    )
                    Toast.makeText(context, "Playback report copied", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            color = DiagnosticsAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        }

        Text(
            text = "COMPATIBILITY REPORT  ›",
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DiagnosticsAmber.copy(alpha = 0.10f),
                    RoundedCornerShape(10.dp),
                )
                .border(
                    1.dp,
                    DiagnosticsAmber.copy(alpha = 0.30f),
                    RoundedCornerShape(10.dp),
                )
                .clickable { onShowCompatibilityReport() }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            color = DiagnosticsAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
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
            .background(DiagnosticsAmber.copy(alpha = 0.12f), shape)
            .border(1.dp, DiagnosticsAmber.copy(alpha = 0.38f), shape)
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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = if (emphasize) DiagnosticsAmber else diagnosticLabelColor(label),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
        )
        Text(
            text = value,
            color = if (emphasize) DiagnosticsAmber else DiagnosticsPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
