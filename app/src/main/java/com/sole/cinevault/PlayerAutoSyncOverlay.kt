package com.sole.cinevault

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.subtitles.AutoSyncStatus
import com.sole.cinevault.subtitles.SubtitleSyncResult
import com.sole.cinevault.ui.theme.*

@Composable
internal fun AutoSyncFloatingIndicator(
    status: AutoSyncStatus,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (status is AutoSyncStatus.Idle) return

    val infiniteTransition = rememberInfiniteTransition(label = "autoSyncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "autoSyncSpinAngle"
    )

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .animateContentSize(animationSpec = tween(220))
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceDeep.copy(alpha = 0.72f))
            .border(1.dp, AmberGlow.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        when (status) {
            is AutoSyncStatus.Analyzing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync, contentDescription = null, tint = AmberCore,
                        modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = spinAngle }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = status.stage, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            is AutoSyncStatus.Success, is AutoSyncStatus.LowConfidence -> {
                val result = if (status is AutoSyncStatus.Success) status.result else (status as AutoSyncStatus.LowConfidence).result
                val highConfidence = status is AutoSyncStatus.Success
                val accentColor = if (highConfidence) Color(0xFF4CAF50) else Color(0xFFFF9800)
                val offsetSeconds = result.initialOffsetMs / 1000f
                val isDrift = result.timeScale != 1.0
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (highConfidence) "Auto-sync complete" else "Possible correction found",
                        color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${if (isDrift) "Starting offset" else "Offset"}: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s",
                        color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (isDrift) {
                        val driftPercent = (result.timeScale - 1.0) * 100.0
                        Text(
                            text = "Drift: ${if (driftPercent >= 0) "+" else ""}${"%.2f".format(driftPercent)}%",
                            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(text = "Confidence: ${(result.confidence * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
                    if (!highConfidence) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Limited matching dialogue found — worth previewing before you commit.",
                            color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Apply", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF4CAF50)).clickable { onApply(result) }.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                        Text(
                            text = "Cancel", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE53935)).clickable { onCancel() }.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            is AutoSyncStatus.Failed -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Couldn't sync", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextMuted,
                            modifier = Modifier.size(15.dp).clickable { onCancel() }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = status.reason, color = TextMuted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try Again", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onRetry() }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
            AutoSyncStatus.Idle -> {}
        }
    }
}
