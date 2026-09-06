package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.subtitles.SpeechSubtitleStatus
import com.sole.cinevault.subtitles.WhisperModelManager
import com.sole.cinevault.ui.theme.*

@Composable
fun SpeechSubtitlePanel(
    status: SpeechSubtitleStatus,
    models: List<WhisperModelManager.ModelInfo>,
    onSelectModel: (WhisperModelManager.ModelId) -> Unit,
    onDownloadModel: (WhisperModelManager.ModelId) -> Unit,
    onDeleteModel: (WhisperModelManager.ModelId) -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val generating = status is SpeechSubtitleStatus.Generating
    val downloading = status is SpeechSubtitleStatus.DownloadingModel
    val busy = generating || downloading
    val selected = models.firstOrNull { it.selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 20.dp, fill = GlassSurfaceStrong.copy(alpha = 0.82f))
            .border(1.dp, AmberCore.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SPEECH → SUBTITLES",
                color = AmberCore,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore.copy(alpha = 0.12f))
                    .border(1.dp, AmberCore.copy(alpha = 0.28f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, "Close", tint = TextMuted)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "SPEECH MODEL",
            color = AmberCore,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(AmberCore.copy(alpha = 0.10f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(5.dp))

        models.forEach { model ->
            ModelRow(
                model = model,
                enabled = !busy,
                onSelect = { onSelectModel(model.id) },
                onDownload = { onDownloadModel(model.id) },
                onDelete = { onDeleteModel(model.id) },
            )
            Spacer(Modifier.height(4.dp))
        }

        if (selected?.installed == true) {
            Button(
                onClick = onGenerate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberCore)
            ) {
                Text("Generate with ${selected.displayName}", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        when (status) {
            is SpeechSubtitleStatus.DownloadingModel ->
                SpeechProgress("Downloading & verifying ${status.fileName}", status.percent)

            is SpeechSubtitleStatus.Generating -> {
                SpeechProgress(status.phase, status.percent)
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Stop transcription")
                }
            }

            is SpeechSubtitleStatus.Ready ->
                Text(
                    "Ready • ${status.cueCount} ${if (status.cueCount == 1) "cue" else "cues"}",
                    color = TextBright,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

            is SpeechSubtitleStatus.Failed ->
                Text(
                    status.reason,
                    color = AmberCore,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

            SpeechSubtitleStatus.Idle -> Unit
        }

        Text(
            "Every downloaded model file is SHA-256 verified before CineVault installs or loads it.",
            color = TextMuted,
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ModelRow(
    model: WhisperModelManager.ModelInfo,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (model.selected) AmberCore.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f))
            .border(
                1.dp,
                AmberCore.copy(alpha = if (model.selected) 0.32f else 0.10f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled && model.installed) { onSelect() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.displayName, color = TextBright, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(5.dp))
                Text(
                    model.tierLabel,
                    color = AmberCore,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AmberCore.copy(alpha = 0.10f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Text(
                "${model.sizeLabel} • ${model.description}",
                color = TextMuted,
                fontSize = 7.8.sp,
                maxLines = 2,
            )
        }

        when {
            model.installed && model.selected -> Text(
                "USE",
                color = Color.Black,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
            model.installed -> {
                Text(
                    "USE",
                    color = AmberCore,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, AmberCore.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable(enabled = enabled) { onSelect() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "DEL",
                    color = TextMuted,
                    fontSize = 7.sp,
                    modifier = Modifier
                        .clickable(enabled = enabled) { onDelete() }
                        .padding(4.dp)
                )
            }
            else -> Text(
                "DOWNLOAD",
                color = AmberCore,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, AmberCore.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable(enabled = enabled) { onDownload() }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SpeechProgress(label: String, percent: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextBright, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("$percent%", color = TextMuted, fontSize = 9.sp)
        }
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        )
    }
}
