package com.sole.cinevault

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
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

    val panelWidth = if (isLandscape) 250.dp else 255.dp
    val panelBottomPadding = if (isLandscape) 92.dp else 235.dp
    val panelEndPadding = if (isLandscape) 14.dp else 14.dp
    val panelMaxHeight = if (isLandscape) 215.dp else 430.dp

    val panelPaddingHorizontal = if (isLandscape) 10.dp else 12.dp
    val panelPaddingVertical = if (isLandscape) 8.dp else 10.dp
    val rowGap = if (isLandscape) 5.dp else 8.dp

    val titleSize = if (isLandscape) 13.sp else 15.sp
    val labelSize = if (isLandscape) 10.sp else 12.sp
    val valueSize = if (isLandscape) 9.sp else 10.sp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.06f))
            .clickable {
                onUserInteraction()
                onDismiss()
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = panelBottomPadding,
                    end = panelEndPadding
                )
                .width(panelWidth)
                .heightIn(max = panelMaxHeight)
                .clip(RoundedCornerShape(if (isLandscape) 18.dp else 22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.58f),
                            Color.Black.copy(alpha = 0.42f)
                        )
                    )
                )
                .clickable { onUserInteraction() }
                .padding(
                    horizontal = panelPaddingHorizontal,
                    vertical = panelPaddingVertical
                ),
            verticalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitle Settings",
                    color = Color.White,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(if (isLandscape) 26.dp else 30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 5.dp else 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SubtitleTopChip(
                    text = "Internal",
                    selected = subtitlesEnabled && hasInternalSubtitles,
                    enabled = hasInternalSubtitles,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUserInteraction()
                        onInternalClick()
                    }
                )

                SubtitleTopChip(
                    text = "SRT",
                    selected = false,
                    enabled = true,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUserInteraction()
                        onDownloadClick()
                    }
                )

                SubtitleTopChip(
                    text = if (subtitlesEnabled) "OFF" else "ON",
                    selected = !subtitlesEnabled,
                    enabled = true,
                    isLandscape = isLandscape,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUserInteraction()
                        onToggleSubtitles()
                    }
                )
            }

            if (!isLandscape) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            }

            CompactSliderRow(
                label = "Text",
                valueLabel = "${currentFontSize.toInt()}sp",
                value = currentFontSize,
                valueRange = 12f..32f,
                steps = 9,
                isLandscape = isLandscape,
                labelSize = labelSize,
                valueSize = valueSize,
                onValueChange = {
                    onUserInteraction()
                    onFontSizeChange(it)
                }
            )

            CompactSliderRow(
                label = "Position",
                valueLabel = "${(currentVerticalPosition * 100).toInt()}%",
                value = currentVerticalPosition,
                valueRange = 0.02f..0.30f,
                steps = 0,
                isLandscape = isLandscape,
                labelSize = labelSize,
                valueSize = valueSize,
                onValueChange = {
                    onUserInteraction()
                    onVerticalPositionChange(it)
                }
            )

            CompactSyncRow(
                currentSyncOffset = currentSyncOffset,
                isLandscape = isLandscape,
                labelSize = labelSize,
                valueSize = valueSize,
                onUserInteraction = onUserInteraction,
                onSyncOffsetChange = onSyncOffsetChange
            )

            if (!isLandscape) {
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Reset",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            onUserInteraction()
                            onReset()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    isLandscape: Boolean,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    onValueChange: (Float) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = valueLabel,
                color = Color(0xFFFFD36A),
                fontSize = valueSize,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 26.dp else 34.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFFFD36A),
                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
            )
        )
    }
}

@Composable
private fun CompactSyncRow(
    currentSyncOffset: Float,
    isLandscape: Boolean,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    onUserInteraction: () -> Unit,
    onSyncOffsetChange: (Float) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Sync",
                color = Color.White,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (currentSyncOffset >= 0f)
                    "+${String.format("%.1f", currentSyncOffset)}s"
                else
                    "${String.format("%.1f", currentSyncOffset)}s",
                color = Color(0xFFFFD36A),
                fontSize = valueSize,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
        ) {
            TinySyncButton(
                text = "-0.1",
                isLandscape = isLandscape
            ) {
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
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 26.dp else 34.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFFFD36A),
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                )
            )

            TinySyncButton(
                text = "+0.1",
                isLandscape = isLandscape
            ) {
                onUserInteraction()
                onSyncOffsetChange((currentSyncOffset + 0.1f).coerceAtMost(5f))
            }
        }
    }
}

@Composable
private fun SubtitleTopChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (selected) Color(0xFFFFD36A).copy(alpha = 0.92f)
                else Color.White.copy(alpha = 0.10f),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) Color.Black else Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        ),
        contentPadding = PaddingValues(
            horizontal = if (isLandscape) 4.dp else 7.dp,
            vertical = if (isLandscape) 3.dp else 5.dp
        ),
        modifier = modifier.height(if (isLandscape) 32.dp else 38.dp)
    ) {
        Text(
            text = text,
            fontSize = if (isLandscape) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun TinySyncButton(
    text: String,
    isLandscape: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(
            horizontal = if (isLandscape) 5.dp else 7.dp,
            vertical = if (isLandscape) 3.dp else 5.dp
        ),
        modifier = Modifier.height(if (isLandscape) 26.dp else 30.dp)
    ) {
        Text(
            text = text,
            fontSize = if (isLandscape) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
