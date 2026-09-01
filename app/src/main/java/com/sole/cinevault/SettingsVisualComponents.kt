package com.sole.cinevault


import com.sole.cinevault.metadata.*
import com.sole.cinevault.library.*
import com.sole.cinevault.smb.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

// ── Hero card with a slow-breathing amber halo around the logo ─────────────
@Composable
internal fun HeroCard() {
    val pulse = rememberInfiniteTransition(label = "hero_pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "hero_glow_alpha"
    )
    val glowScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "hero_glow_scale"
    )

    // Height and internal spacing both tightened — text sat noticeably far
    // from the logo before, with more empty vertical space than the content
    // actually needed.
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp)
            .shadow(28.dp, RoundedCornerShape(28.dp), ambientColor = AmberCore.copy(alpha = 0.35f), spotColor = AmberCore.copy(alpha = 0.5f))
            .glassPanel(cornerRadius = 28.dp, fill = SpaceMid)
            .border(1.dp, Brush.linearGradient(listOf(AmberCore.copy(alpha = 0.55f), Color.Transparent, AmberCore.copy(alpha = 0.25f))), RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(text = "CineVault", color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(text = "Premium Media Experience", color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Your personal cinema archive.", color = TextMuted, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).size(78.dp),
            contentAlignment = Alignment.Center
        ) {
            // Breathing glow ring behind the logo
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .scale(glowScale)
                    .background(
                        Brush.radialGradient(
                            listOf(AmberCore.copy(alpha = glowAlpha * 0.55f), Color.Transparent),
                            radius = 115f
                        ),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier.size(68.dp).clip(CircleShape).background(GlassSurface),
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.cinevault_circle_logo), contentDescription = "CineVault Logo", modifier = Modifier.size(64.dp))
            }
        }
    }
}

// ── Glass section card with a glowing accent icon chip in the header ────────
@Composable
internal fun GlassSectionCard(title: String, subtitle: String, icon: ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.35f))
            .glassPanel(cornerRadius = 24.dp, fill = GlassSurface)
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = TextBright, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

// ── Glass action row ─────────────────────────────────────────────────────────
@Composable
internal fun GlassActionRow(icon: ImageVector, iconTint: Color = AmberCore, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .glassPanel(cornerRadius = 22.dp, fill = GlassSurfaceFaint)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(23.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = action, color = iconTint, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

// ── Glowing outlined button used for "Add Network Share" ────────────────────
@Composable
internal fun GlowButton(text: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright),
        modifier = Modifier
            .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = accent.copy(alpha = 0.4f), spotColor = accent.copy(alpha = 0.5f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

// Same breathing-glow recipe as VideoPlayerScreen.kt's FrostedPlayButton —
// 0.45→0.95 alpha, 1400ms, FastOutSlowInEasing, reversing — reused here so
// "Add Folder" and every folder pill pulse with the identical rhythm and
// intensity instead of a different, one-off glow animation.
@Composable
private fun rememberPlayButtonStyleGlow(): Float {
    val infinite = rememberInfiniteTransition(label = "matchPlayGlow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "matchPlayGlowAlpha"
    )
    return glowAlpha
}

// "Add Folder" — same glow language as the play button (radial bloom behind
// a gradient border, both driven by rememberPlayButtonStyleGlow), just
// shaped as a pill instead of a circle.
@Composable
internal fun AddFolderGlowPill(onClick: () -> Unit) {
    val glowAlpha = rememberPlayButtonStyleGlow()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GlassSurfaceStrong)
            .background(Brush.radialGradient(colors = listOf(AmberGlow.copy(alpha = glowAlpha * 0.55f), Color.Transparent), radius = 220f))
            .border(
                width = 1.4.dp,
                brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.75f + 0.2f * glowAlpha), AmberDeep.copy(alpha = 0.30f))),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = AmberCore, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add Folder", color = TextBright, fontWeight = FontWeight.Bold)
    }
}

// One folder — wrap-content pill (sizes itself to the name, not a full-width
// row), tall enough to comfortably touch-and-hold, glowing in its own color
// from FolderPillPalette using the exact same play-button glow recipe.
// Long-press opens the removal confirmation instead of a trailing delete icon.
// Leading icon now reflects the folder's likely source (TikTok/Instagram/
// WhatsApp/Camera/generic) via settingsFolderIconFor instead of always
// showing the same plain folder glyph.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderNamePill(name: String, accent: Color, onLongPress: () -> Unit) {
    val glowAlpha = rememberPlayButtonStyleGlow()
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GlassSurfaceStrong)
            .background(Brush.radialGradient(colors = listOf(accent.copy(alpha = glowAlpha * 0.45f), Color.Transparent), radius = 170f))
            .border(
                width = 1.3.dp,
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.75f + 0.2f * glowAlpha), accent.copy(alpha = 0.30f))),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = settingsFolderIconFor(name), contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = name, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SmbShareRow(share: SmbShare, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(SpaceDeep.copy(alpha = 0.60f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Dns, contentDescription = null, tint = AccentNetwork, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = share.displayName, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${share.host}/${share.shareName}", color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Remove", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
        }
    }
}

// ── Signature footer: "Ash" with a gentle glow + build-date line ────────────
@Composable
internal fun SignatureFooter() {
    val pulse = rememberInfiniteTransition(label = "sig_pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(2600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "sig_glow_alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft glow bloom sitting behind the signature text
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(70.dp)
                    .background(
                        Brush.radialGradient(listOf(AmberCore.copy(alpha = glowAlpha * 0.35f), Color.Transparent)),
                        RoundedCornerShape(50)
                    )
            )
            Text(
                text = "Ash",
                color = AmberCore.copy(alpha = glowAlpha),
                fontSize = 40.sp,
                fontFamily = AshSignatureFont,
                letterSpacing = 0.5.sp
            )
        }
        // Pulled up closer to the signature — this used to sit a full
        // line's worth of space below "Ash" with nothing filling the gap.
        Box(modifier = Modifier.offset(y = (-10).dp)) {
            Text(
                text = "Crafting CineVault since May 2026",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
