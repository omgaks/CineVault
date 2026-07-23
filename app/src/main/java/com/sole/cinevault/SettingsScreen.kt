package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private val AshSignatureFont = FontFamily(
    Font(R.font.great_vibes)
)

// ── Accent palette for section icon chips (keeps amber as the anchor, adds variety) ──
private val AccentNetwork = Color(0xFF6FC3FF)
private val AccentStream = Color(0xFFC792FF)
private val AccentSupport = Color(0xFFFF6E8C)
private val AccentAbout = Color(0xFFE8C77A)

// Distinct color per folder pill — cycled by position so every added folder
// reads as visually its own thing rather than a uniform list.
private val FolderPillPalette = listOf(
    Color(0xFFFFC94D), // amber-gold
    Color(0xFF6FC3FF), // sky blue
    Color(0xFFC792FF), // violet
    Color(0xFFFF6E8C), // rose
    Color(0xFF7CE0C3), // mint
    Color(0xFFFF9F6E)  // coral
)

@Composable
fun SettingsScreen(
    // No longer used by any section in this screen (Scan Manager, the only
    // thing that called it, was removed — it just opened Library and did
    // nothing else). Left in the signature rather than removed, since
    // removing it would also require an edit to MainActivity.kt's call site
    // for no real benefit.
    onOpenScanSources: () -> Unit,
    // FIX: previously took no argument, so the URL typed into the Stream
    // dialog was captured then silently discarded — Play did nothing.
    // Now the URL is actually passed through to whoever handles playback.
    onOpenStreamUrl: (String) -> Unit
) {
    val context = LocalContext.current
    var showStreamDialog by remember { mutableStateOf(false) }

    var smbShares by remember { mutableStateOf(loadSmbShares(context)) }
    var showSmbDialog by remember { mutableStateOf(false) }
    var editingShare by remember { mutableStateOf<SmbShare?>(null) }

    // FIX: these dialogs are plain full-screen overlays with no connection to
    // the system back gesture — Settings is the bottom of the nav stack, so
    // without this, swiping back while a dialog is open fell through to
    // Android's default behavior (closing the app) instead of dismissing
    // the dialog and returning to Settings.
    BackHandler(enabled = showStreamDialog) { showStreamDialog = false }
    BackHandler(enabled = showSmbDialog) { showSmbDialog = false; editingShare = null }

    // Select Folder — the general "Add Media Folder" picker (and the whole
    // Media Library section) was removed entirely: it saved folders but
    // nothing ever scanned them, so it did nothing in practice. This picker
    // already does everything that one was supposed to, and actually works
    // — it scans with its own rules (no duration/size floor, no personal-
    // video filename filter), groups as one poster card in Library, stays
    // out of Home/Continue Watching, and only downloads subtitles when you
    // manually tap Download inside the player.
    var restrictedFolders by remember { mutableStateOf(loadRestrictedFolders(context)) }
    var folderPendingRemoval by remember { mutableStateOf<RestrictedFolder?>(null) }
    val restrictedFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() } ?: "Folder"
            addRestrictedFolder(context, name, uri.toString())
            restrictedFolders = loadRestrictedFolders(context)
        }
    }
    BackHandler(enabled = folderPendingRemoval != null) { folderPendingRemoval = null }

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header text removed — "Settings" was redundant with the
            // bottom-nav tab already showing which screen this is.

            HeroCard()

            Spacer(modifier = Modifier.height(18.dp))

            // Network Shares (SMB) — scans a NAS/PC share into the same library
            GlassSectionCard(title = "Network Shares", subtitle = "Scan videos from a NAS or PC share (SMB) into your library.", icon = Icons.Rounded.Dns, accent = AccentNetwork) {
                GlowButton(text = "Add Network Share", icon = Icons.Rounded.Dns, accent = AccentNetwork) {
                    editingShare = null; showSmbDialog = true
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (smbShares.isEmpty()) {
                    Text(text = "No network shares added yet.", color = TextMuted, fontSize = 14.sp)
                } else {
                    smbShares.forEach { share ->
                        SmbShareRow(
                            share = share,
                            onEdit = { editingShare = share; showSmbDialog = true },
                            onDelete = {
                                removeSmbShare(context, share.id)
                                smbShares = loadSmbShares(context)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "After adding a share, go to Library and rescan to pull its videos in.", color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stream Player — opens the stream dialog right here instead of redirecting
            GlassSectionCard(title = "Stream Player", subtitle = "Play direct online video links.", icon = Icons.Rounded.Language, accent = AccentStream) {
                GlassActionRow(icon = Icons.Rounded.Language, iconTint = AccentStream, title = "Stream URL", subtitle = "Play MP4 / M3U8 / WEBM links instantly", action = "OPEN") { showStreamDialog = true }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "For direct video links only. Torrent/magnet links are not supported.", color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Select Folder — pills glow with the exact same recipe as the
            // player's breathing play button (see rememberPlayButtonStyleGlow
            // below), just re-colored per pill instead of amber-only.
            GlassSectionCard(title = "Select Folder", subtitle = "Kept out of Home & Continue Watching. Visible in Library and Search only.", icon = Icons.Rounded.Folder, accent = AccentSupport) {
                AddFolderGlowPill { restrictedFolderPicker.launch(null) }
                Spacer(modifier = Modifier.height(16.dp))
                if (restrictedFolders.isEmpty()) {
                    Text(text = "No folder added yet.", color = TextMuted, fontSize = 14.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        restrictedFolders.forEachIndexed { index, folder ->
                            FolderNamePill(
                                name = folder.displayName,
                                accent = FolderPillPalette[index % FolderPillPalette.size],
                                onLongPress = { folderPendingRemoval = folder }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Touch and hold a folder to remove it. After adding a folder, go to Library and rescan to pull its files in.", color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Support
            GlassSectionCard(title = "Support CineVault", subtitle = "A small thank you keeps the vault alive.", icon = Icons.Filled.Favorite, accent = AccentSupport) {
                GlassActionRow(icon = Icons.Filled.Favorite, iconTint = AccentSupport, title = "Buy me a coffee", subtitle = "Optional support / donate button", action = "\u2665") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/")))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // About — rewritten to actually describe what CineVault does
            // today instead of the generic launch-era copy.
            GlassSectionCard(title = "About", subtitle = "Premium local cinema experience.", icon = Icons.Filled.Info, accent = AccentAbout) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = AccentAbout, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "CineVault v2.0", color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your personal cinema, built from the ground up. Play straight from local storage, a USB drive, or a NAS over SMB — with real decoding for DTS, TrueHD and the formats most players choke on. TMDB and OMDB automatically bring in posters, cast, genres, collections, and IMDb/Rotten Tomatoes ratings for everything you own. A cinematic glass-and-amber design throughout, gesture-driven playback, and a private Select Folder space that stays exactly that.",
                    color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            SignatureFooter()

            Spacer(modifier = Modifier.height(90.dp))
        }

        // Stream URL dialog — opens right here in Settings
        if (showStreamDialog) {
            StreamUrlDialog(
                onDismiss = { showStreamDialog = false },
                onPlayUrl = { url ->
                    showStreamDialog = false
                    onOpenStreamUrl(url)
                }
            )
        }

        // SMB share add/edit dialog
        if (showSmbDialog) {
            SmbShareDialog(
                existing = editingShare,
                onDismiss = { showSmbDialog = false; editingShare = null },
                onSave = { share ->
                    addOrUpdateSmbShare(context, share)
                    smbShares = loadSmbShares(context)
                    showSmbDialog = false
                    editingShare = null
                }
            )
        }

        // Folder-removal confirmation — replaces the old inline delete icon.
        // Long-press a pill instead; this is the "are you sure" step for it.
        val target = folderPendingRemoval
        if (target != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).clickable { folderPendingRemoval = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(290.dp)
                        .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .clickable(enabled = false) { }
                        .padding(20.dp)
                ) {
                    Text(text = "Remove this folder?", color = TextBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${target.displayName}\" will be removed from Select Folder. The files themselves aren't touched.",
                        color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Cancel", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { folderPendingRemoval = null }.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                        Text(
                            text = "Remove", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF5252)).clickable {
                                removeRestrictedFolder(context, target.id)
                                restrictedFolders = loadRestrictedFolders(context)
                                folderPendingRemoval = null
                            }.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Hero card with a slow-breathing amber halo around the logo ─────────────
@Composable
private fun HeroCard() {
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
private fun GlassSectionCard(title: String, subtitle: String, icon: ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
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
private fun GlassActionRow(icon: ImageVector, iconTint: Color = AmberCore, title: String, subtitle: String, action: String, onClick: () -> Unit) {
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
private fun GlowButton(text: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
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
private fun AddFolderGlowPill(onClick: () -> Unit) {
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderNamePill(name: String, accent: Color, onLongPress: () -> Unit) {
    val glowAlpha = rememberPlayButtonStyleGlow()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GlassSurfaceStrong)
            .background(Brush.radialGradient(colors = listOf(accent.copy(alpha = glowAlpha * 0.5f), Color.Transparent), radius = 200f))
            .border(
                width = 1.3.dp,
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.75f + 0.2f * glowAlpha), accent.copy(alpha = 0.30f))),
                shape = RoundedCornerShape(50)
            )
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = name, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmbShareRow(share: SmbShare, onEdit: () -> Unit, onDelete: () -> Unit) {
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
private fun SignatureFooter() {
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
