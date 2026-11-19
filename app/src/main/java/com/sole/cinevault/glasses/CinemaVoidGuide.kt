package com.sole.cinevault.glasses

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import kotlinx.coroutines.delay

internal data class GuideRow(val icon: ImageVector, val title: String, val detail: String)

/** What the tablet touch surface does while a film plays on the glasses. */
internal val PLAYER_GUIDE_ROWS = listOf(
    GuideRow(Icons.Rounded.TouchApp, "Tap", "Show the controls on the glasses"),
    GuideRow(Icons.Rounded.OpenWith, "Controls showing", "One finger moves the pointer, tap presses, hold then drag slides the bar"),
    GuideRow(Icons.Rounded.PlayCircle, "Double tap", "Play or pause"),
    GuideRow(Icons.Rounded.FastForward, "Swipe sideways, top half", "Seek through the film"),
    GuideRow(Icons.Rounded.WbSunny, "Swipe up/down, left edge", "Brightness"),
    GuideRow(Icons.Rounded.VolumeUp, "Swipe up/down, right edge", "Volume"),
    GuideRow(Icons.Rounded.ZoomOutMap, "Two fingers", "Pinch to zoom, drag to move the picture"),
    GuideRow(Icons.Rounded.Speed, "Hold 2, 3 or 4 fingers still", "Play at 2x, 3x or 5x until you let go"),
    GuideRow(Icons.Rounded.BackHand, "Five fingers spread", "Come back to the tablet"),
)

/** What the tablet touch surface does while the glasses show the app (no film playing). */
internal val APP_GUIDE_ROWS = listOf(
    GuideRow(Icons.Rounded.OpenWith, "One finger", "Move the pointer (the page stays still)"),
    GuideRow(Icons.Rounded.TouchApp, "Tap", "Press what the pointer is on"),
    GuideRow(Icons.Rounded.SwapVert, "Two fingers", "Scroll the page, over the list you want"),
    GuideRow(Icons.AutoMirrored.Rounded.ArrowBack, "Two-finger tap", "Go back"),
    GuideRow(Icons.Rounded.PanTool, "Hold, then drag", "Slide a slider or bar"),
    GuideRow(Icons.Rounded.BackHand, "Five fingers spread", "Come back to the tablet"),
)

/**
 * The gesture guide card. With [autoHideMs] it fades out after that long (used while a film
 * plays, so it never sits on top of the picture controls for good); null keeps it visible.
 */
@Composable
internal fun CinemaVoidGuide(
    rows: List<GuideRow>,
    autoHideMs: Long?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(autoHideMs) {
        if (autoHideMs != null) {
            delay(autoHideMs)
            visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "cinemaVoidGuideAlpha",
    )
    if (alpha <= 0.01f) return

    Column(
        modifier = modifier.alpha(alpha).widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Your tablet is the touchpad",
            color = AmberCore,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = AmberCore,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(row.title, color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(row.detail, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
