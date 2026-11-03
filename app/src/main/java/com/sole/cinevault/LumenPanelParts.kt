package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.TextMuted

/** Title row for the standalone Lumen panels: the title in the accent colour and a 40 dp close button. */
@Composable
internal fun LumenPanelHeader(title: String, accent: Color, onClose: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = accent,
            fontSize = LumenType.title,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Close"; role = Role.Button }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("×", color = TextMuted, fontSize = 22.sp) }
        }
    }
}

/** Straight progress bar in the accent colour with a cut end. */
@Composable
internal fun LumenProgressBar(percent: Int, accent: Color, modifier: Modifier = Modifier) {
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 4.dp, bottomEnd = 0.dp, bottomStart = 4.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color.White.copy(alpha = 0.08f), shape)
            .border(1.dp, accent.copy(alpha = 0.5f), shape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .background(accent, shape)
        )
    }
}

/** Compact cut-corner button (not full width) for row actions; 40 dp tall. */
@Composable
internal fun LumenSmallButton(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)
    val a = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (filled) accent.copy(alpha = 0.85f * a) else accent.copy(alpha = 0.10f * a), shape)
            .border(1.2.dp, accent.copy(alpha = a), shape)
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = if (filled) Color(0xFF05060A).copy(alpha = a) else accent.copy(alpha = a),
            fontSize = LumenType.caption,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
