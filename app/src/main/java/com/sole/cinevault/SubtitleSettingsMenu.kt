package com.sole.cinevault

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleSettingsMenu(
    isVisible: Boolean,
    subtitlesEnabled: Boolean,
    hasInternalSubtitles: Boolean,
    onInternalClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onDismiss: () -> Unit,
    currentFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    currentVerticalPosition: Float,
    onVerticalPositionChange: (Float) -> Unit,
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onReset: () -> Unit,
    onUserInteraction: () -> Unit
) {
    if (!isVisible) return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f))
            .clickable {
                onUserInteraction()
                onDismiss()
            }
    ) {


        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = if (isLandscape) 265.dp else 320.dp,
                    end = 22.dp
                )
                .width(if (isLandscape) 260.dp else 270.dp)
                .wrapContentHeight()
                .heightIn(max = if (isLandscape) 360.dp else 520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.46f),
                            Color.Black.copy(alpha = 0.42f)
                        )
                    )
                )
                .clickable { onUserInteraction() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitle Settings",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleTopChip(
                    text = "Internal",
                    selected = subtitlesEnabled && hasInternalSubtitles,
                    enabled = hasInternalSubtitles,
                    onClick = {
                        onUserInteraction()
                        onInternalClick()
                    }
                )

                SubtitleTopChip(
                    text = "SRT",
                    selected = false,
                    enabled = true,
                    onClick = {
                        onUserInteraction()
                        onDownloadClick()
                    }
                )

                SubtitleTopChip(
                    text = if (subtitlesEnabled) "OFF" else "ON",
                    selected = !subtitlesEnabled,
                    enabled = true,
                    onClick = {
                        onUserInteraction()
                        onToggleSubtitles()
                    }
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Text Size",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(7.dp))

                    Text(
                        text = "${currentFontSize.toInt()}sp",
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("A-", color = Color.White.copy(alpha = 0.60f), fontSize = 13.sp)

                    Slider(
                        value = currentFontSize,
                        onValueChange = {
                            onUserInteraction()
                            onFontSizeChange(it)
                        },
                        valueRange = 12f..32f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFFFD36A),
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        )
                    )

                    Text("A+", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Vertical Position",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(7.dp))

                    Text(
                        text = "${(currentVerticalPosition * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 11.sp
                    )
                }

                Slider(
                    value = currentVerticalPosition,
                    onValueChange = {
                        onUserInteraction()
                        onVerticalPositionChange(it)
                    },
                    valueRange = 0.02f..0.30f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFFFD36A),
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Subtitle Sync",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(7.dp))

                    Text(
                        text = if (currentSyncOffset >= 0f)
                            "+${String.format("%.1f", currentSyncOffset)}s"
                        else
                            "${String.format("%.1f", currentSyncOffset)}s",
                        color = Color(0xFFFFD36A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TinySyncButton("-0.1s") {
                        onUserInteraction()
                        onSyncOffsetChange((currentSyncOffset - 0.1f).coerceAtLeast(-5f))
                    }

                    Slider(
                        value = currentSyncOffset,
                        onValueChange = {
                            onUserInteraction()
                            onSyncOffsetChange(it)
                        },
                        valueRange = -5f..5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFFFD36A),
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        )
                    )

                    TinySyncButton("+0.1s") {
                        onUserInteraction()
                        onSyncOffsetChange((currentSyncOffset + 0.1f).coerceAtMost(5f))
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            TextButton(
                onClick = {
                    onUserInteraction()
                    onReset()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Reset to Defaults",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SubtitleTopChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (selected) Color(0xFFFFD36A).copy(alpha = 0.90f)
                else Color.White.copy(alpha = 0.10f),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) Color.Black else Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TinySyncButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}