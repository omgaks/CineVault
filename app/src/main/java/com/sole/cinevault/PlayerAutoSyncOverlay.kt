package com.sole.cinevault

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.AutoSyncStatus
import com.sole.cinevault.subtitles.SubtitleSyncResult
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/**
 * Auto-sync notice in the Lumen frame: rose when it failed, mint when it is sure, gold when it is
 * not, sky while it works. Every notice says what happened and what to do next.
 */
@Composable
internal fun AutoSyncFloatingIndicator(
    status: AutoSyncStatus,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (status is AutoSyncStatus.Idle) return

    val accent = when (status) {
        is AutoSyncStatus.Analyzing -> LumenSky
        is AutoSyncStatus.Success -> LumenMint
        is AutoSyncStatus.LowConfidence -> LumenGold
        is AutoSyncStatus.Failed -> LumenRose
        AutoSyncStatus.Idle -> LumenSky
    }

    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .animateContentSize(animationSpec = tween(220))
            .lumenFrame(accent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            is AutoSyncStatus.Analyzing -> {
                Text("Syncing", color = accent, fontSize = LumenType.title, fontWeight = FontWeight.Bold)
                Text(status.stage.ifBlank { "Listening to the film" }, color = TextBright, fontSize = LumenType.body)
            }
            is AutoSyncStatus.Success, is AutoSyncStatus.LowConfidence -> {
                val result = if (status is AutoSyncStatus.Success) status.result else (status as AutoSyncStatus.LowConfidence).result
                val sure = status is AutoSyncStatus.Success
                val offsetSeconds = result.initialOffsetMs / 1000f
                val isDrift = result.timeScale != 1.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (sure) "Synced" else "Possible fix",
                        color = accent,
                        fontSize = LumenType.title,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${if (offsetSeconds >= 0f) "+" else "−"}${"%.2f".format(kotlin.math.abs(offsetSeconds))} s",
                        color = accent,
                        fontSize = LumenType.value,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "${(result.confidence * 100).toInt()}% sure." +
                        if (sure) "" else " Not much matching dialogue, so preview before you keep it.",
                    color = TextBright,
                    fontSize = LumenType.body,
                )
                if (isDrift) {
                    val driftPercent = (result.timeScale - 1.0) * 100.0
                    Text(
                        "Drift ${if (driftPercent >= 0) "+" else "−"}${"%.2f".format(kotlin.math.abs(driftPercent))}% over the film.",
                        color = TextMuted,
                        fontSize = LumenType.caption,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    LumenActionButton("Apply", accent, Modifier.weight(1f)) { onApply(result) }
                    LumenActionButton("Cancel", TextBright, Modifier.weight(1f)) { onCancel() }
                }
            }
            is AutoSyncStatus.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Couldn't sync", color = accent, fontSize = LumenType.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCancel() },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", color = TextMuted, fontSize = LumenType.value) }
                }
                Text(status.reason, color = TextBright, fontSize = LumenType.body)
                Text(
                    "Try again, or open the Deck and set the delay by hand.",
                    color = TextMuted,
                    fontSize = LumenType.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    LumenActionButton("Try again", LumenGold, Modifier.weight(1f)) { onRetry() }
                    LumenActionButton("Close", TextBright, Modifier.weight(1f)) { onCancel() }
                }
            }
            AutoSyncStatus.Idle -> {}
        }
    }
}
