package com.sole.cinevault.subtitles

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassBorderBottom
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.SpaceMid
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.glassPanel

fun subtitleMenuWidth(screenWidthDp: Float, isLandscape: Boolean): Dp =
    if (isLandscape) (screenWidthDp * 0.22f).dp.coerceIn(168.dp, 210.dp)
    else (screenWidthDp * 0.44f).dp.coerceIn(176.dp, 230.dp)

/**
 * Tap-CC HUD.
 *
 * Pick / Time / Look / Make open the same four rooms as long-press Studio.
 * Size + position use the shared Low/Mid/High scale (0.02 / 0.16 / 0.30).
 */
@Composable
fun SubtitleSettingsMenu(
    isVisible: Boolean,
    subtitlesEnabled: Boolean,
    activeTrackStatusText: String = "",
    onToggleSubtitles: () -> Unit,
    onTracksClick: () -> Unit,
    onFindClick: () -> Unit,
    onSyncClick: () -> Unit,
    onStyleClick: () -> Unit,
    onDismiss: () -> Unit,
    currentFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    currentVerticalPosition: Float,
    onVerticalPositionChange: (Float) -> Unit,
    onReset: () -> Unit,
    onUserInteraction: () -> Unit,
    onMakeClick: () -> Unit = onFindClick
) {
    if (!isVisible) return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val popupWidth = subtitleMenuWidth(configuration.screenWidthDp.toFloat(), isLandscape)
    val titleSize = if (isLandscape) 12.sp else 13.sp
    val statusSize = if (isLandscape) 9.sp else 9.5.sp

    Column(
        modifier = Modifier
            .width(popupWidth)
            .glassPanel(cornerRadius = if (isLandscape) 18.dp else 22.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .clickable { onUserInteraction() }
            .padding(horizontal = if (isLandscape) 11.dp else 13.dp, vertical = if (isLandscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 9.dp else 11.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Subtitles",
                    color = TextBright,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Switch(
                    checked = subtitlesEnabled,
                    onCheckedChange = { onUserInteraction(); onToggleSubtitles() },
                    modifier = Modifier.height(if (isLandscape) 18.dp else 20.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmberCore,
                        checkedTrackColor = AmberGlow.copy(alpha = 0.45f)
                    )
                )
            }
            if (activeTrackStatusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activeTrackStatusText,
                    color = TextMutedSafe,
                    fontSize = statusSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            QuickIconAction(Icons.Filled.ViewList, "Pick", Modifier.weight(1f), isLandscape) {
                onUserInteraction(); onTracksClick()
            }
            QuickIconAction(Icons.Filled.Sync, "Time", Modifier.weight(1f), isLandscape) {
                onUserInteraction(); onSyncClick()
            }
            QuickIconAction(Icons.Filled.Palette, "Look", Modifier.weight(1f), isLandscape) {
                onUserInteraction(); onStyleClick()
            }
            QuickIconAction(Icons.Rounded.AutoAwesome, "Make", Modifier.weight(1f), isLandscape) {
                onUserInteraction(); onMakeClick()
            }
        }

        HorizontalDivider(color = GlassBorderBottom)

        QuickStepperRow(
            label = "Size",
            valueText = "${currentFontSize.toInt()}",
            onDecrease = { onUserInteraction(); onFontSizeChange((currentFontSize - 1f).coerceIn(12f, 32f)) },
            onIncrease = { onUserInteraction(); onFontSizeChange((currentFontSize + 1f).coerceIn(12f, 32f)) },
            isLandscape = isLandscape
        )

        QuickPositionRow(
            currentValue = currentVerticalPosition,
            onSelect = { onUserInteraction(); onVerticalPositionChange(it) },
            isLandscape = isLandscape
        )

        Text(
            text = "↺  Reset look",
            color = AmberCore,
            fontSize = if (isLandscape) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(GlassSurface)
                .clickable { onUserInteraction(); onReset() }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private val TextMutedSafe = Color(0xFFA8A6A0)

@Composable
private fun QuickIconAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    isLandscape: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, AmberCore.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = if (isLandscape) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = AmberCore, modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label.uppercase(),
            color = TextBright,
            fontSize = if (isLandscape) 7.sp else 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickStepperRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    isLandscape: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color(0xFFC9A765),
            fontSize = if (isLandscape) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        StepperButton(Icons.Filled.Remove, isLandscape, onDecrease)
        Text(
            text = valueText,
            color = AmberCore,
            fontSize = if (isLandscape) 10.sp else 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(if (isLandscape) 28.dp else 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepperButton(Icons.Filled.Add, isLandscape, onIncrease)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, isLandscape: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (isLandscape) 22.dp else 24.dp)
            .clip(CircleShape)
            .background(GlassSurface)
            .border(1.dp, AmberCore.copy(alpha = 0.4f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = AmberCore, modifier = Modifier.size(if (isLandscape) 11.dp else 12.dp))
    }
}

private fun positionBandLabel(value: Float): String = when {
    value < 0.09f -> "Low"
    value < 0.23f -> "Mid"
    value < 0.55f -> "High"
    else -> "Top"
}

@Composable
private fun QuickPositionRow(currentValue: Float, onSelect: (Float) -> Unit, isLandscape: Boolean) {
    val currentBand = positionBandLabel(currentValue)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Position",
            color = Color(0xFFC9A765),
            fontSize = if (isLandscape) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Low" to 0.02f, "Mid" to 0.16f, "High" to 0.30f).forEach { (label, value) ->
                val selected = currentBand == label
                Text(
                    text = label,
                    color = if (selected) Color.Black else TextBright,
                    fontSize = if (isLandscape) 8.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AmberCore else GlassSurface)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}
