package com.sole.cinevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sole.cinevault.subtitles.SubtitleGenerationStatus
import com.sole.cinevault.subtitles.SubtitleTranslationEngine
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

@Composable
fun SubtitleGenerationMenu(
    status: SubtitleGenerationStatus,
    modelReady: Boolean,
    modelName: String,
    modelSizeLabel: String,
    onDownloadModel: () -> Unit,
    onGenerate: () -> Unit,
    onTranslate: (SubtitleTranslationEngine.SupportedLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = status is SubtitleGenerationStatus.DownloadingModel ||
        status is SubtitleGenerationStatus.Generating ||
        status is SubtitleGenerationStatus.Translating

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong)
                .padding(20.dp)
        ) {
            Text("AI Subtitles", color = TextBright, fontWeight = FontWeight.Bold)

            if (!modelReady) {
                Text(
                    "$modelName is an optional $modelSizeLabel download. " +
                        "It is stored separately from CineVault, so it does not increase the APK size.",
                    color = TextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(
                    onClick = onDownloadModel,
                    enabled = !busy,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Download Standard Model ($modelSizeLabel)")
                }
            } else {
                Text("$modelName installed ✓", color = TextMuted, modifier = Modifier.padding(top = 10.dp))
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    Button(onClick = onGenerate, enabled = !busy) {
                        Text("Generate from audio")
                    }
                }
            }

            when (status) {
                is SubtitleGenerationStatus.DownloadingModel ->
                    ProgressRow("Downloading — ${status.fileName}", status.percent)
                is SubtitleGenerationStatus.Generating ->
                    ProgressRow("Transcribing — ${status.phase}", status.percent)
                is SubtitleGenerationStatus.Translating ->
                    ProgressRow("Translating — ${status.phase}", status.percent)
                is SubtitleGenerationStatus.Failed ->
                    Text(status.reason, color = AmberCore, modifier = Modifier.padding(top = 12.dp))
                is SubtitleGenerationStatus.Ready ->
                    Text(
                        if (status.cueCount >= 0) "Generated ${status.cueCount} lines" else "Ready",
                        color = TextBright,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                SubtitleGenerationStatus.Idle -> Unit
            }

            Text(
                "Translate current subtitle to:",
                color = TextBright,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(220.dp),
            ) {
                items(SubtitleTranslationEngine.commonTargetLanguages) { lang ->
                    OutlinedButton(
                        onClick = { onTranslate(lang) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(lang.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, percent: Int) {
    Column(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
        Text(label, color = TextBright)
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
