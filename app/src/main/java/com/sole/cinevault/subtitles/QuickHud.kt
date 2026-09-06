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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

private const val DELAY_STEP_SECONDS = 0.1f

/**
 * Tap-CC destination — replaces the old 4-item Dock entirely. A single
 * draggable strip: filename, Delay (drag or +/- step, real waveform once
 * Auto-Sync has run), Size, and Position. Position is vertical-only — see
 * the call site for why a real "x,y" control isn't built here yet.
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
    onReset: () -> Unit,
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
                // 20% narrower than the original 240-300dp range.
                .widthIn(min = 192.dp, max = 240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurfaceStrong)
                .padding(9.dp)
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
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = subtitleFileName ?: "No subtitle loaded",
                    color = TextBright,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Delay gets its own layout (label + value pill on one line,
            // step buttons either side of the slider on the next) since
            // it's the one control that needs both a quick nudge and a
            // fine drag — cramming that into the generic single-line
            // HudRow would either hide the buttons or crush the slider.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Delay", color = TextMuted, fontSize = 8.sp, modifier = Modifier.weight(1f))
                HudValuePill(formatDelay(delaySeconds))
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DelayStepButton(symbol = "\u2212") { onDelayChange((delaySeconds - DELAY_STEP_SECONDS).coerceIn(-10f, 10f)) }
                Spacer(modifier = Modifier.width(4.dp))
                WaveformSlider(
                    value = delaySeconds.coerceIn(-10f, 10f),
                    onValueChange = onDelayChange,
                    valueRange = -10f..10f,
                    speechTimeline = speechTimeline,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                DelayStepButton(symbol = "+") { onDelayChange((delaySeconds + DELAY_STEP_SECONDS).coerceIn(-10f, 10f)) }
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
            HudRow(label = "Position", valueText = null) {
                DotThumbSlider(
                    value = bottomPadding,
                    onValueChange = onBottomPaddingChange,
                    valueRange = 0.02f..0.90f,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "Reset",
                color = TextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onReset() }
                    .padding(vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun DelayStepButton(symbol: String, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = AmberCore,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(AmberCore.copy(alpha = 0.14f))
            .clickable { onClick() }
    )
}

// The "amber glow" value badge used everywhere a control's current value
// is shown — replaces bare Text, which read too small and too easy to
// miss against the glass background at this size.
@Composable
private fun HudValuePill(text: String) {
    Text(
        text = text,
        color = AmberCore,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AmberCore.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun HudRow(label: String, valueText: String?, slider: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = TextMuted, fontSize = 8.sp, modifier = Modifier.width(34.dp))
        slider()
        if (valueText != null) {
            Spacer(modifier = Modifier.width(5.dp))
            HudValuePill(valueText)
        }
    }
}

private fun formatDelay(seconds: Float): String =
    if (seconds >= 0f) "+${"%.1f".format(seconds)}s" else "${"%.1f".format(seconds)}s"
