package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.SpeechSubtitleStatus
import com.sole.cinevault.subtitles.WhisperModelManager
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/** Speech to subtitles, in the Lumen frame: pick a Whisper model, download it, generate. */
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
    onBack: (() -> Unit)? = null,
) {
    val generating = status is SpeechSubtitleStatus.Generating
    val downloading = status is SpeechSubtitleStatus.DownloadingModel
    val busy = generating || downloading
    val selected = models.firstOrNull { it.selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lumenFrame(LumenGold)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        LumenPanelHeader("SPEECH TO SUBTITLES", LumenGold, onDismiss, onBack)

        // The header stays reachable while the models and actions scroll on small windows.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            LumenSectionLabel("Speech model", LumenGold)
            models.forEach { model ->
                ModelRow(
                    model = model,
                    enabled = !busy,
                    onSelect = { onSelectModel(model.id) },
                    onDownload = { onDownloadModel(model.id) },
                    onDelete = { onDeleteModel(model.id) },
                )
                Spacer(Modifier.height(6.dp))
            }

            if (selected?.installed == true) {
                Spacer(Modifier.height(2.dp))
                LumenActionButton("Generate with ${selected.displayName}", LumenGold, enabled = !busy) { onGenerate() }
            }

            when (status) {
                is SpeechSubtitleStatus.DownloadingModel ->
                    SpeechProgress("Downloading and checking ${status.fileName}", status.percent, LumenSky)

                is SpeechSubtitleStatus.Generating -> {
                    SpeechProgress(status.phase, status.percent, LumenGold)
                    Spacer(Modifier.height(8.dp))
                    LumenActionButton("Stop transcription", LumenRose) { onStop() }
                }

                is SpeechSubtitleStatus.Ready ->
                    Text(
                        "Ready. ${status.cueCount} ${if (status.cueCount == 1) "line" else "lines"} written.",
                        color = LumenMint,
                        fontSize = LumenType.body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                is SpeechSubtitleStatus.Failed ->
                    Text(
                        status.reason,
                        color = LumenRose,
                        fontSize = LumenType.body,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                SpeechSubtitleStatus.Idle -> Unit
            }

            Text(
                "Every model file is checked (SHA-256) before CineVault installs or loads it. Everything runs on this device.",
                color = TextMuted,
                fontSize = LumenType.caption,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (model.selected) LumenGold.copy(alpha = 0.14f) else Color_White05, LumenCardShape)
            .border(1.dp, if (model.selected) LumenGold else LumenGold.copy(alpha = 0.25f), LumenCardShape)
            .clickable(
                enabled = enabled && model.installed,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect() }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.displayName, color = TextBright, fontSize = LumenType.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(model.tierLabel.uppercase(), color = LumenGold, fontSize = LumenType.caption, fontWeight = FontWeight.Bold)
        }
        Text("${model.sizeLabel}. ${model.description}", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                model.installed && model.selected ->
                    LumenSmallButton("In use", LumenGold, filled = true, enabled = false) {}
                model.installed -> {
                    LumenSmallButton("Use", LumenGold, enabled = enabled) { onSelect() }
                    LumenSmallButton("Delete", LumenRose, enabled = enabled) { onDelete() }
                }
                else -> LumenSmallButton("Download", LumenGold, enabled = enabled) { onDownload() }
            }
        }
    }
}

private val Color_White05 = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)

@Composable
private fun SpeechProgress(label: String, percent: Int, accent: androidx.compose.ui.graphics.Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextBright, fontSize = LumenType.body, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("$percent%", color = accent, fontSize = LumenType.value, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        LumenProgressBar(percent, accent)
    }
}
