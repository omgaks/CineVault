package com.sole.cinevault.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes

// ============================================================
//  CineVault design kit, in code.
//  Pills, chips, badges, the toggle, the close button and the
//  rating logos. Every size follows CineType (12sp minimum).
// ============================================================

/** Glass card: dark blue-grey at ~55%, 1dp edge. A gel tints the edge when given. */
fun Modifier.cineCard(radius: Dp = 26.dp, gel: Color? = null): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(CineGlassFill)
        .border(1.dp, gel?.copy(alpha = 0.45f) ?: CineGlassEdge, shape)
}

enum class CineButtonStyle { Primary, Secondary, Quiet, Danger }

@Composable
fun CineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CineButtonStyle = CineButtonStyle.Primary,
    enabled: Boolean = true
) {
    val shape = CircleShape
    val base = modifier
        .defaultMinSize(minHeight = 48.dp)
        .clip(shape)
    val styled = when (style) {
        CineButtonStyle.Primary -> base
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(AmberCore, AmberGlow)))
        CineButtonStyle.Secondary -> base
            .background(AmberCore.copy(alpha = 0.10f))
            .border(1.dp, AmberCore.copy(alpha = 0.55f), shape)
        CineButtonStyle.Quiet -> base
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
        CineButtonStyle.Danger -> base
            .background(GelRose.copy(alpha = 0.10f))
            .border(1.dp, GelRose.copy(alpha = 0.6f), shape)
    }
    val textColor = when (style) {
        CineButtonStyle.Primary -> Color(0xFF1A1205)
        CineButtonStyle.Secondary -> AmberCore
        CineButtonStyle.Quiet -> TextBright
        CineButtonStyle.Danger -> GelRose
    }
    Box(
        modifier = styled
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else textColor.copy(alpha = 0.4f),
            fontSize = CineType.Body,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/** Selectable chip. Off = neutral glass, on = lit in [gel] (amber by default). */
@Composable
fun CineChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gel: Color = AmberCore,
    count: String? = null
) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(if (selected) gel.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) gel.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) gel else TextBright,
            fontSize = CineType.Label,
            fontWeight = FontWeight.Bold
        )
        if (count != null) {
            Text(text = count, color = (if (selected) gel else TextBright).copy(alpha = 0.8f), fontSize = CineType.Caption, fontFamily = PlexMonoFamily)
        }
    }
}

/** Small status badge, such as "2 copies", "4K HDR", "Scene ahead". */
@Composable
fun CineBadge(
    text: String,
    modifier: Modifier = Modifier,
    gel: Color? = null
) {
    val shape = RoundedCornerShape(if (gel != null) 50 else 8)
    val color = gel ?: TextBright
    Text(
        text = text,
        color = color,
        fontSize = CineType.Caption,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(shape)
            .then(if (gel != null) Modifier.background(gel.copy(alpha = 0.16f)) else Modifier)
            .border(1.dp, (gel ?: Color.White).copy(alpha = if (gel != null) 0.65f else 0.18f), shape)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/**
 * Close button for every window. Looks 40dp, but the tap area is 48dp.
 * Always placed top right.
 */
@Composable
fun CineCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Close" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = TextBright, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Toggle from the design kit: ember-rose glow when OFF, amber glow when ON.
 * The word ON/OFF can sit beside it ([showLabel]) so colour is never the only signal.
 */
@Composable
fun CineToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 30.dp
) {
    val knob = trackHeight - 8.dp
    val pad = 3.dp
    val onColor = AmberCore
    val offColor = GelRose
    val accent by animateColorAsState(if (checked) onColor else offColor, tween(160), label = "toggleAccent")
    val knobX by animateDpAsState(
        if (checked) trackWidth - knob - pad - 2.dp else pad,
        tween(160), label = "toggleKnob"
    )
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(trackWidth, trackHeight)
                .shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (checked) 0.30f else 0.16f))
                .border(1.dp, accent.copy(alpha = 0.7f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobX, y = (trackHeight - knob) / 2 - 1.dp)
                    .size(knob)
                    .shadow(8.dp, CircleShape, ambientColor = accent, spotColor = accent)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        if (showLabel) {
            Text(
                text = if (checked) "ON" else "OFF",
                color = accent,
                fontSize = CineType.Label,
                fontFamily = PlexMonoFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}

/**
 * Rating chip with the real service logo: dark glass, logo 16dp, value 12sp.
 * Rotten Tomatoes turns green below 60%, matching the earlier behaviour.
 */
@Composable
fun RatingLogoChip(
    @DrawableRes logo: Int,
    description: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xB305060A))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), shape)
            .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = description,
            modifier = Modifier.height(16.dp),
            contentScale = ContentScale.Fit,
            colorFilter = tint?.let { ColorFilter.tint(it) }
        )
        Spacer(Modifier.width(5.dp))
        Text(text = value, color = TextBright, fontSize = CineType.Caption, fontWeight = FontWeight.ExtraBold)
    }
}
