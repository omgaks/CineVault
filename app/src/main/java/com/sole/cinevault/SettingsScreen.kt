package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

private val AshSignatureFont = FontFamily(
    Font(R.font.great_vibes)
)

@Composable
fun SettingsScreen(
    onOpenScanSources: () -> Unit,
    // FIX: previously took no argument, so the URL typed into the Stream
    // dialog was captured then silently discarded — Play did nothing.
    // Now the URL is actually passed through to whoever handles playback.
    onOpenStreamUrl: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedFolders by remember { mutableStateOf(loadMediaFolders(context)) }
    var showStreamDialog by remember { mutableStateOf(false) }

    var smbShares by remember { mutableStateOf(loadSmbShares(context)) }
    var showSmbDialog by remember { mutableStateOf(false) }
    var editingShare by remember { mutableStateOf<SmbShare?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val updated = selectedFolders + uri.toString()
            selectedFolders = updated.distinct()
            saveMediaFolders(context, selectedFolders)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(text = "Settings", color = TextBright, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text(text = "CineVault control room", color = TextMuted, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(22.dp))

            // Hero card
            Box(
                modifier = Modifier.fillMaxWidth().height(155.dp)
                    .glassPanel(cornerRadius = 28.dp, fill = SpaceMid)
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(text = "CineVault", color = TextBright, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Premium Media Experience", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Your personal cinema archive.", color = TextMuted, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).size(86.dp).clip(RoundedCornerShape(50)).background(GlassSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(id = R.drawable.cinevault_circle_logo), contentDescription = "CineVault Logo", modifier = Modifier.size(82.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scan Manager — stays in Settings, opens library for actual scanning
            GlassSectionCard(title = "Scan Manager", subtitle = "Choose default scan sources and keep library clean.") {
                GlassActionRow(icon = Icons.Filled.Settings, title = "Scan Sources", subtitle = "Movies, TV Shows, Downloads, Anime, Camera", action = "OPEN") { onOpenScanSources() }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "After changing scan sources, go to Library and rescan.", color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Network Shares (SMB) — scans a NAS/PC share into the same library
            GlassSectionCard(title = "Network Shares", subtitle = "Scan videos from a NAS or PC share (SMB) into your library.") {
                Button(
                    onClick = { editingShare = null; showSmbDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright)
                ) {
                    Icon(imageVector = Icons.Rounded.Dns, contentDescription = null, tint = AmberCore, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Network Share", fontWeight = FontWeight.Bold)
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
            GlassSectionCard(title = "Stream Player", subtitle = "Play direct online video links.") {
                GlassActionRow(icon = Icons.Rounded.Language, title = "Stream URL", subtitle = "Play MP4 / M3U8 / WEBM links instantly", action = "OPEN") { showStreamDialog = true }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "For direct video links only. Torrent/magnet links are not supported.", color = TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Media Library folders
            GlassSectionCard(title = "Media Library", subtitle = "Add folders where CineVault should scan videos.") {
                Button(
                    onClick = { folderPicker.launch(null) },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright)
                ) {
                    Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = AmberCore, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Media Folder", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "Selected Media Folders", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                if (selectedFolders.isEmpty()) {
                    Text(text = "No folder selected yet.", color = TextMuted, fontSize = 14.sp)
                } else {
                    selectedFolders.forEach { folder -> FolderRow(folder = folder) }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Support
            GlassSectionCard(title = "Support CineVault", subtitle = "A small thank you keeps the vault alive.") {
                GlassActionRow(icon = Icons.Filled.Favorite, iconTint = Color(0xFFFFC107), title = "Buy me a coffee", subtitle = "Optional support / donate button", action = "♥") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/")))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // About
            GlassSectionCard(title = "About", subtitle = "Premium local cinema experience.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "CineVault v2.0", color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Built for local videos, cinematic posters, subtitles, resume playback and a premium media library feel.", color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = "Ashish \u2022 2026",
                color = AmberCore.copy(alpha = 0.92f),
                fontSize = 34.sp,
                fontFamily = AshSignatureFont,
                letterSpacing = 0.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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
    }
}

// ── Glass section card ───────────────────────────────────────────────────────
@Composable
private fun GlassSectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .glassPanel(cornerRadius = 24.dp, fill = GlassSurface)
            .padding(16.dp)
    ) {
        Text(text = title, color = TextBright, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
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
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(iconTint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(23.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = action, color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun FolderRow(folder: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(SpaceDeep.copy(alpha = 0.60f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = AmberCore, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Text(text = folder, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Icon(imageVector = Icons.Rounded.Dns, contentDescription = null, tint = AmberCore, modifier = Modifier.size(17.dp))
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

private fun saveMediaFolders(context: Context, folders: List<String>) {
    context.getSharedPreferences("cinevault_settings", Context.MODE_PRIVATE).edit().putStringSet("media_folders", folders.toSet()).apply()
}

private fun loadMediaFolders(context: Context): List<String> {
    return context.getSharedPreferences("cinevault_settings", Context.MODE_PRIVATE).getStringSet("media_folders", emptySet())?.toList() ?: emptyList()
}
