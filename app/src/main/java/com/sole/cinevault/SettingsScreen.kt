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

internal val AshSignatureFont = FontFamily(
    Font(R.font.great_vibes)
)

// ── Accent palette for section icon chips (keeps amber as the anchor, adds variety) ──
internal val AccentNetwork = Color(0xFF6FC3FF)
private val AccentStream = Color(0xFFC792FF)
private val AccentSupport = Color(0xFFFF6E8C)
private val AccentAbout = Color(0xFFE8C77A)
private val AccentPrivacy = Color(0xFF8FD9A8)

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

// ── Folder type icon heuristic ────────────────────────────────────────────
// Generic Material icons only — deliberately NOT actual TikTok/Instagram
// brand marks (those are trademarked assets, not something to reproduce).
// Matches on the folder's display name, which for Select-Folder entries is
// usually whatever the source app named its export/download folder.
internal fun settingsFolderIconFor(displayName: String): ImageVector {
    val lower = displayName.lowercase()
    return when {
        lower.contains("tiktok") -> Icons.Filled.MusicNote
        lower.contains("instagram") || lower.contains("insta") -> Icons.Filled.PhotoCamera
        lower.contains("whatsapp") -> Icons.Filled.Chat
        lower.contains("camera") || lower.contains("dcim") -> Icons.Filled.CameraAlt
        else -> Icons.Rounded.Folder
    }
}

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
    var showCrashLog by remember { mutableStateOf(false) }

    var smbShares by remember { mutableStateOf(loadSmbShares(context)) }
    var showSmbDialog by remember { mutableStateOf(false) }
    var editingShare by remember { mutableStateOf<SmbShare?>(null) }

    // FIX: these dialogs are plain full-screen overlays with no connection to
    // the system back gesture — Settings is the bottom of the nav stack, so
    // without this, swiping back while a dialog is open fell through to
    // Android's default behavior (closing the app) instead of dismissing
    // the dialog and returning to Settings.
    BackHandler(enabled = showStreamDialog) { showStreamDialog = false }
    BackHandler(enabled = showCrashLog) { showCrashLog = false }
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
            // Layout changed from a vertical Column to a wrapping FlowRow —
            // previously each folder pill stacked on its own line, which
            // grew the card's height fast with more than a couple of
            // folders added. Now they flow left-to-right and wrap onto a
            // new line only when they run out of horizontal room, same
            // reading direction as every other chip/pill row in the app
            // (categories, genres, tech badges).
            GlassSectionCard(title = "Select Folder", subtitle = "Kept out of Home & Continue Watching. Visible in Library and Search only.", icon = Icons.Rounded.Folder, accent = AccentSupport) {
                AddFolderGlowPill { restrictedFolderPicker.launch(null) }
                Spacer(modifier = Modifier.height(16.dp))
                if (restrictedFolders.isEmpty()) {
                    Text(text = "No folder added yet.", color = TextMuted, fontSize = 14.sp)
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

            // Privacy — metadata fetch toggle. Off means no more TMDB/OMDB
            // network calls from here on (search, ratings, or the upgrade
            // path that runs even on a cache hit if some fields are
            // missing) — whatever's already cached keeps showing, nothing
            // new gets looked up. Read fresh from prefs on entry, saved
            // immediately on toggle — no separate Save button, matching
            // every other setting on this screen.
            GlassSectionCard(title = "Privacy", subtitle = "Control what CineVault sends over the network.", icon = Icons.Rounded.Lock, accent = AccentPrivacy) {
                var metadataFetchEnabled by remember { mutableStateOf(loadMetadataFetchEnabled(context)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Fetch online metadata", color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Posters, ratings, cast, and genres from TMDB/OMDB. Turning this off uses only what's already cached for each video — nothing new is looked up.",
                            color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = metadataFetchEnabled,
                        onCheckedChange = {
                            metadataFetchEnabled = it
                            saveMetadataFetchEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f))
                    )
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
                Spacer(modifier = Modifier.height(14.dp))
                GlassActionRow(
                    icon = Icons.Filled.Info, iconTint = AccentAbout,
                    title = "View Crash Log", subtitle = "For diagnosing crashes — screenshot and share",
                    action = "OPEN"
                ) { showCrashLog = true }
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

        // Crash log viewer — reads the file installCrashLogger() writes to
        // (MainActivity.kt). Scrollable text so it can be screenshotted in
        // pieces if it's long; Clear empties the file for a fresh start.
        if (showCrashLog) {
            val logText = remember(showCrashLog) { readCrashLog(context) }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)).clickable { showCrashLog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 480.dp)
                        .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .clickable(enabled = false) { }
                        .padding(18.dp)
                ) {
                    Text(text = "Crash Log", color = TextBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text(
                            text = logText.ifBlank { "No crashes logged yet." },
                            color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // FIX: added Copy — the log text wasn't selectable, so
                    // there was no way to get it out of the dialog other
                    // than a screenshot (useless for a bug report someone
                    // needs to read and paste elsewhere, e.g. into a chat
                    // with Claude). Copies straight to the clipboard via
                    // ClipboardManager, independent of whether in-app text
                    // selection works at all. Placed first/leftmost since
                    // it's the action this dialog gets opened for most.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Copy", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("CineVault Crash Log", logText.ifBlank { "No crashes logged yet." }))
                                Toast.makeText(context, "Crash log copied", Toast.LENGTH_SHORT).show()
                            }.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                        Text(
                            text = "Close", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { showCrashLog = false }.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                        Text(
                            text = "Clear Log", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF5252)).clickable { clearCrashLog(context); showCrashLog = false }.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
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
