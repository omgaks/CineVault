package com.sole.cinevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.sole.cinevault.subtitles.SubtitleGenerationStatus
import com.sole.cinevault.subtitles.SubtitleTranslationEngine
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

/**
 * Compact AI subtitle panel.
 *
 * Positioning/dragging is owned by VideoPlayerScreen through the existing
 * DraggableFloatingPopup wrapper, so this composable only owns content.
 * The panel can always be closed — even while a long-running job continues.
 */
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
    val busy =
        status is SubtitleGenerationStatus.DownloadingModel ||
            status is SubtitleGenerationStatus.Generating ||
            status is SubtitleGenerationStatus.Translating

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(
                cornerRadius = 22.dp,
                fill = GlassSurfaceStrong,
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "AI Subtitles",
                color = TextBright,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close AI Subtitles",
                    tint = TextMuted,
                )
            }
        }

        if (!modelReady) {
            Text(
                "$modelName • optional $modelSizeLabel download",
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Stored separately from the CineVault APK.",
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )

            Button(
                onClick = onDownloadModel,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Download Standard Model")
            }
        } else {
            Text(
                "$modelName installed ✓",
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )

            Button(
                onClick = onGenerate,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Generate from audio")
            }
        }

        when (status) {
            is SubtitleGenerationStatus.DownloadingModel ->
                ProgressRow("Downloading model", status.percent)

            is SubtitleGenerationStatus.Generating ->
                ProgressRow(
                    if (status.phase.equals("Transcribing", ignoreCase = true)) {
                        "Transcribing"
                    } else {
                        status.phase
                    },
                    status.percent,
                )

            is SubtitleGenerationStatus.Translating ->
                ProgressRow("Translating", status.percent)

            is SubtitleGenerationStatus.Failed ->
                Text(
                    status.reason,
                    color = AmberCore,
                    modifier = Modifier.padding(top = 10.dp),
                )

            is SubtitleGenerationStatus.Ready ->
                Text(
                    if (status.cueCount >= 0) {
                        "Generated ${status.cueCount} subtitle lines"
                    } else {
                        "Subtitle ready"
                    },
                    color = TextBright,
                    modifier = Modifier.padding(top = 10.dp),
                )

            SubtitleGenerationStatus.Idle -> Unit
        }

        Text(
            "Translate current subtitle",
            color = TextBright,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(160.dp),
        ) {
            items(SubtitleTranslationEngine.commonTargetLanguages) { lang ->
                OutlinedButton(
                    onClick = { onTranslate(lang) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        lang.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(
    label: String,
    percent: Int,
) {
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                color = TextBright,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$percent%",
                color = TextMuted,
            )
        }

        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}
