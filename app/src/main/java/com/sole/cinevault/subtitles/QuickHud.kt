package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

/**
 * Tap-CC destination — replaces the old 4-item Dock entirely. A single
 * draggable strip: filename, Delay (real waveform once Auto-Sync has run),
 * Size, and Position. Position is vertical-only — see the call site for
 * why a real "x,y" control isn't built here yet.
 */
@Composable
fun QuickHud(
    subtitleFileName: String?,
    delaySeconds: Float,
    onDelayChange: (Float) -> Unit,
    speechTimeline: FloatArray?,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    containerSize: IntSize,
    initialOffset: Offset,
    modifier: Modifier = Modifier
) {
    DraggableStudioWindow(
        initialOffset = initialOffset,
        containerSize = containerSize,
        modifier = modifier
    ) { dragHandleModifier ->
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurfaceStrong)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().then(dragHandleModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Drag to move",
                    tint = TextFaint,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subtitleFileName ?: "No subtitle loaded",
                    color = TextBright,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            HudRow(label = "Delay", valueText = formatDelay(delaySeconds)) {
                WaveformSlider(
                    value = delaySeconds.coerceIn(-10f, 10f),
                    onValueChange = onDelayChange,
                    valueRange = -10f..10f,
                    speechTimeline = speechTimeline,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            HudRow(label = "Size", valueText = "${fontSizeSp.toInt()}sp") {
                DotThumbSlider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..32f,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Vertical-only, deliberately — see doc comment on the public
            // function above. Labeled "Position", not "x,y", so the UI
            // never claims a capability that isn't actually there.
            HudRow(label = "Position", valueText = "") {
                DotThumbSlider(
                    value = bottomPadding,
                    onValueChange = onBottomPaddingChange,
                    valueRange = 0.02f..0.90f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HudRow(label: String, valueText: String, slider: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = TextMuted, fontSize = 8.5.sp, modifier = Modifier.width(38.dp))
        slider()
        if (valueText.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = valueText, color = AmberCore, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(30.dp))
        }
    }
}

private fun formatDelay(seconds: Float): String =
    if (seconds >= 0f) "+${"%.1f".format(seconds)}s" else "${"%.1f".format(seconds)}s"
