package com.sole.cinevault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.SpeechSubtitleStatus
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

@Composable
fun SpeechSubtitlePanel(
    status: SpeechSubtitleStatus,
    modelReady: Boolean,
    modelName: String,
    modelSizeLabel: String,
    onDownloadModel: () -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val generating = status is SpeechSubtitleStatus.Generating
    val downloading = status is SpeechSubtitleStatus.DownloadingModel
    val busy = generating || downloading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 22.dp, fill = GlassSurfaceStrong)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Speech → Subtitles",
                color = TextBright,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, "Close", tint = TextMuted)
            }
        }

        Text(
            if (modelReady) "$modelName installed ✓"
            else "$modelName • optional $modelSizeLabel download",
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (!modelReady) {
            Button(
                onClick = onDownloadModel,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Download Whisper model")
            }
        } else {
            Button(
                onClick = onGenerate,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Generate from audio")
            }
        }

        when (status) {
            is SpeechSubtitleStatus.DownloadingModel ->
                SpeechProgress("Downloading model", status.percent)

            is SpeechSubtitleStatus.Generating -> {
                SpeechProgress(status.phase, status.percent)
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text("Stop transcription")
                }
            }

            is SpeechSubtitleStatus.Ready ->
                Text(
                    "Ready • ${status.cueCount} ${if (status.cueCount == 1) "cue" else "cues"}",
                    color = TextBright,
                    modifier = Modifier.padding(top = 10.dp),
                )

            is SpeechSubtitleStatus.Failed ->
                Text(
                    status.reason,
                    color = AmberCore,
                    modifier = Modifier.padding(top = 10.dp),
                )

            SpeechSubtitleStatus.Idle -> Unit
        }

        Text(
            "Whisper is only required for speech recognition. Translation works separately.",
            color = TextMuted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SpeechProgress(label: String, percent: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextBright, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("$percent%", color = TextMuted)
        }
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}
