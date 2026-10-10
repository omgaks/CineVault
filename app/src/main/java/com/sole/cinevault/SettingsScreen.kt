package com.sole.cinevault

import androidx.compose.material.icons.rounded.Collections
import com.sole.cinevault.collections.loadCollectionPageV2Enabled
import com.sole.cinevault.collections.saveCollectionPageV2Enabled

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.tvmode.TelevisionModeDetector
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.*

internal val AshSignatureFont = FontFamily(
    Font(R.font.great_vibes)
)

// ── Accent palette for section icon chips (keeps amber as the anchor, adds variety) ──
internal val AccentNetwork = GelSky
private val AccentStream = GelViolet
private val AccentSupport = GelRose
private val AccentAbout = GelGold
private val AccentPrivacy = GelMint
private val AccentReset = GelCoral

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


private enum class SettingsGroup(val title: String, val icon: ImageVector, val gel: Color) {
    Library("Library", Icons.Rounded.Folder, GelRose),
    Network("Network", Icons.Rounded.Dns, GelSky),
    Playback("Playback", Icons.Filled.MusicNote, GelViolet),
    Tutorial("Tutorial", Icons.Filled.Info, GelGold),
    You("You & app", Icons.Filled.Favorite, GelMint)
}

@Composable
fun SettingsScreen(
    // No longer used by any section in this screen (Scan Manager, the only
    // thing that called it, was removed). Left in the signature so the
    // MainActivity.kt call site does not need to change.
    onOpenScanSources: () -> Unit,
    onOpenNetworkHub: () -> Unit,
    onOpenStreamUrl: (String) -> Unit,
    onOpenGlassesGestureTutorial: () -> Unit,
    onOpenLibraryTools: () -> Unit = {}
) {
    val context = LocalContext.current
    var showStreamDialog by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showResetSettingsConfirm by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf(SettingsGroup.You) }

    var smbShares by remember { mutableStateOf(loadSmbShares(context)) }
    var showSmbDialog by remember { mutableStateOf(false) }
    var editingShare by remember { mutableStateOf<SmbShare?>(null) }

    // Switch and name state lives here (not inside each card) so a Reset can
    // refresh every switch straight away, and so switching panes on a tablet
    // never loses state.
    var metadataFetchEnabled by remember { mutableStateOf(loadMetadataFetchEnabled(context)) }
    var collectionPageV2 by remember { mutableStateOf(loadCollectionPageV2Enabled(context)) }
    var displayName by remember { mutableStateOf(loadDisplayName(context)) }

    // Back always closes the front-most window first (Tutorial has its own
    // handler inside the hub).
    BackHandler(enabled = showStreamDialog) { showStreamDialog = false }
    BackHandler(enabled = showCrashLog) { showCrashLog = false }
    BackHandler(enabled = showResetSettingsConfirm) { showResetSettingsConfirm = false }
    BackHandler(enabled = showSmbDialog) { showSmbDialog = false; editingShare = null }
    BackHandler(enabled = showNameDialog) { showNameDialog = false }

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

    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val twoPane = rememberCineWidthClass() == CineWidthClass.Expanded

    val tvKeys: Modifier = if (isTelevision) {
        Modifier.onKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
            when (keyEvent.key) {
                Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                else -> false
            }
        }
    } else {
        Modifier
    }

    // ── Sections. Each is one card; the layout below decides where they go. ──

    val quickSwitchesSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Quick switches",
            subtitle = "Two common choices, one tap each.",
            icon = Icons.Rounded.Lock,
            accent = AccentPrivacy
        ) {
            SettingsSwitchRow(
                title = "Fetch online metadata",
                description = "Posters, ratings, cast and genres from TMDB/OMDB. Off uses only what is already saved. Nothing new is looked up.",
                checked = metadataFetchEnabled,
                isTelevision = isTelevision,
                onCheckedChange = {
                    metadataFetchEnabled = it
                    saveMetadataFetchEnabled(context, it)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSwitchRow(
                title = "New collection page",
                description = "Shows films in a franchise you don't own yet, in release order, with progress and a next-up. Off gives the classic grid.",
                checked = collectionPageV2,
                isTelevision = isTelevision,
                onCheckedChange = {
                    collectionPageV2 = it
                    saveCollectionPageV2Enabled(context, it)
                }
            )
        }
    }

    val nameSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Your name",
            subtitle = "Used in the Home greeting. Stays on this device.",
            icon = Icons.Rounded.Edit,
            accent = AccentAbout
        ) {
            TvFocusableSlot(isTelevision = isTelevision, onActivate = { showNameDialog = true }) {
                GlassActionRow(
                    icon = Icons.Rounded.Edit,
                    iconTint = AccentAbout,
                    title = if (displayName.isBlank()) "Add your name" else displayName,
                    subtitle = if (displayName.isBlank()) "Optional" else "Tap to change",
                    action = "EDIT"
                ) { showNameDialog = true }
            }
        }
    }

    val tutorialSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Tutorial",
            subtitle = "Short guides to every part of CineVault.",
            icon = Icons.Filled.Info,
            accent = AccentAbout
        ) {
            TvFocusableSlot(isTelevision = isTelevision, onActivate = { showTutorial = true }) {
                GlassActionRow(
                    icon = Icons.Filled.Info,
                    iconTint = AccentAbout,
                    title = "Open Tutorial",
                    subtitle = "${TUTORIAL_GUIDES.size} guides: gestures, studios, glasses, network, TV and more",
                    action = "OPEN"
                ) { showTutorial = true }
            }
        }
    }

    val networkSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Network",
            subtitle = "Sources, nearby devices and private library sharing.",
            icon = Icons.Rounded.Dns,
            accent = AccentNetwork
        ) {
            TvFocusableSlot(isTelevision = isTelevision, onActivate = onOpenNetworkHub) {
                GlassActionRow(
                    icon = Icons.Rounded.Dns,
                    iconTint = AccentNetwork,
                    title = "Open Network Hub",
                    subtitle = "SMB, nearby devices, media servers, web sources & sharing",
                    action = "OPEN",
                    onClick = onOpenNetworkHub
                )
            }
            if (smbShares.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${smbShares.size} SMB share${if (smbShares.size == 1) "" else "s"} already saved.",
                    color = TextFaint,
                    fontSize = CineType.Caption,
                    lineHeight = 17.sp
                )
            }
        }
    }

    val streamSection: @Composable () -> Unit = {
        GlassSectionCard(title = "Stream a link", subtitle = "Play direct online video links.", icon = Icons.Rounded.Language, accent = AccentStream) {
            TvFocusableSlot(isTelevision = isTelevision, onActivate = { showStreamDialog = true }) {
                GlassActionRow(icon = Icons.Rounded.Language, iconTint = AccentStream, title = "Stream URL", subtitle = "Play MP4 / M3U8 / WEBM links instantly", action = "OPEN") { showStreamDialog = true }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "For direct video links only. Torrent/magnet links are not supported.", color = TextFaint, fontSize = CineType.Caption, lineHeight = 17.sp)
        }
    }

    val glassesSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Glasses Mode",
            subtitle = "Learn the controls and practice supported head gestures.",
            icon = Icons.Filled.Info,
            accent = AccentAbout
        ) {
            GlassActionRow(
                icon = Icons.Filled.Info,
                iconTint = AccentAbout,
                title = "Glasses controls & gesture practice",
                subtitle = "Touchpad, emergency return, nod and shake",
                action = "OPEN"
            ) { onOpenGlassesGestureTutorial() }
        }
    }

    val playerPointerSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Player settings",
            subtitle = "Subtitles, audio and picture live in the player.",
            icon = Icons.Filled.MusicNote,
            accent = GelViolet
        ) {
            Text(
                text = "Open any film and use the controls inside the player to change subtitles, audio and picture. Reset below puts those choices back to defaults.",
                color = TextMuted,
                fontSize = CineType.Label,
                lineHeight = 19.sp
            )
        }
    }

    val foldersSection: @Composable () -> Unit = {
        GlassSectionCard(title = "Select folders", subtitle = "Kept out of Home & Continue Watching. Still visible in Library and Search.", icon = Icons.Rounded.Folder, accent = AccentSupport) {
            TvFocusableSlot(isTelevision = isTelevision, shape = CircleShape, onActivate = { restrictedFolderPicker.launch(null) }) {
                AddFolderGlowPill { restrictedFolderPicker.launch(null) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (restrictedFolders.isEmpty()) {
                Text(text = "No folder added yet.", color = TextMuted, fontSize = CineType.Label)
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
                Text(text = "Touch and hold a folder to remove it. After adding a folder, go to Library and rescan to pull its files in.", color = TextFaint, fontSize = CineType.Caption, lineHeight = 17.sp)
            }
        }
    }

    val libraryToolsSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Library tools",
            subtitle = "Local artwork and match review.",
            icon = Icons.Rounded.Collections,
            accent = AccentAbout
        ) {
            TvFocusableSlot(isTelevision = isTelevision, onActivate = onOpenLibraryTools) {
                GlassActionRow(
                    icon = Icons.Rounded.Collections,
                    iconTint = AccentAbout,
                    title = "Open Library tools",
                    subtitle = "Import artwork kept beside your films, and fix doubtful matches",
                    action = "OPEN",
                    onClick = onOpenLibraryTools
                )
            }
        }
    }

    val secretHelpSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Secret folder",
            subtitle = "A locked Library category for private films.",
            icon = Icons.Rounded.Lock,
            accent = AccentPrivacy
        ) {
            Text(
                text = "Open Library and choose the Secret category. It opens with your fingerprint or PIN and locks again when you leave. This is different from Select folders above, which only hides a folder from Home.",
                color = TextMuted,
                fontSize = CineType.Label,
                lineHeight = 19.sp
            )
        }
    }

    val voiceSection: @Composable () -> Unit = {
        var micDenied by remember { mutableStateOf(false) }
        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                VoiceRuntime.enabled = true
                saveVoiceEnabled(context, true)
                micDenied = false
            } else {
                micDenied = true
            }
        }
        // Reading this makes the readout below redraw as the numbers change.
        @Suppress("UNUSED_VARIABLE") val readoutTick = VoiceRuntime.statsVersion
        GlassSectionCard(
            title = "Voice (beta)",
            subtitle = "Tap the microphone and say \"play Avengers Endgame\", or say \"Hey CineVault\".",
            icon = Icons.Filled.Mic,
            accent = GelMint
        ) {
            SettingsSwitchRow(
                title = "Voice control",
                description = "Off by default. Adds a microphone button on Home and Library. Tap it and say what to play. Uses your phone's built-in offline recogniser, or the free speech model that subtitle generation uses (see Speech engine below). Everything is heard on this phone only. Nothing is recorded or sent.",
                checked = VoiceRuntime.enabled,
                isTelevision = isTelevision,
                onCheckedChange = { on ->
                    when {
                        !on -> {
                            VoiceRuntime.enabled = false
                            saveVoiceEnabled(context, false)
                        }
                        hasMicrophonePermission(context) -> {
                            VoiceRuntime.enabled = true
                            saveVoiceEnabled(context, true)
                            micDenied = false
                        }
                        else -> micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            if (micDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Voice needs the microphone. Allow it in Android Settings, then switch Voice on again.",
                    color = GelRose,
                    fontSize = CineType.Caption,
                    lineHeight = 17.sp
                )
            }
            if (VoiceRuntime.enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSwitchRow(
                    title = "Always listen for the wake word",
                    description = "While CineVault is open and the screen is on, listen for \"Hey CineVault\". Switch this off to use only the microphone button.",
                    checked = VoiceRuntime.wakeListening,
                    isTelevision = isTelevision,
                    onCheckedChange = {
                        VoiceRuntime.wakeListening = it
                        saveVoiceWakeListening(context, it)
                    }
                )
            }
            if (VoiceRuntime.enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSwitchRow(
                    title = "Show microphone button",
                    description = "The gold microphone on Home, Library and the player. If the wake word works well for you, switch this off. Saying \"Hey CineVault\" still opens the Listening card.",
                    checked = VoiceRuntime.showMic,
                    isTelevision = isTelevision,
                    onCheckedChange = {
                        VoiceRuntime.showMic = it
                        saveVoiceShowMic(context, it)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSwitchRow(
                title = "Show what I heard",
                description = "Shows the words it heard on the microphone card, and a small note when the wake word is heard.",
                checked = VoiceRuntime.showHeard,
                isTelevision = isTelevision,
                onCheckedChange = {
                    VoiceRuntime.showHeard = it
                    saveVoiceShowHeard(context, it)
                }
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text("Speech engine", color = TextBright, fontSize = CineType.Body, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Which recogniser turns your voice into words. Both work offline and nothing leaves this phone. After the wake word, Whisper double-checks you really said it, if it is downloaded.",
                color = TextMuted,
                fontSize = CineType.Caption,
                lineHeight = 17.sp
            )
            SpeechEngine.values().forEach { engine ->
                Spacer(modifier = Modifier.height(12.dp))
                val selected = VoiceRuntime.engine == engine
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, if (selected) GelGold else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                        .clickable {
                            VoiceRuntime.engine = engine
                            saveVoiceEngine(context, engine)
                        }
                        .padding(14.dp)
                ) {
                    Text(
                        text = (if (selected) "● " else "○ ") + engine.label,
                        color = if (selected) GelGold else TextBright,
                        fontSize = CineType.Body,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(engine.description, color = TextMuted, fontSize = CineType.Caption, lineHeight = 17.sp)
                }
            }
            Text(
                text = if (androidOnDeviceSpeechReady(context)) "This phone: Android's offline recogniser is ready." else "This phone: Android's offline recogniser is not available, so Whisper is used.",
                color = TextMuted,
                fontSize = CineType.Caption,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text("Wake phrases", color = TextBright, fontSize = CineType.Body, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tick one or more. If one doesn't work for you, tick another. At least one stays on.",
                color = TextMuted,
                fontSize = CineType.Caption,
                lineHeight = 17.sp
            )
            VoiceWakePhrase.values().forEach { phrase ->
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSwitchRow(
                    title = phrase.label,
                    description = phrase.description,
                    checked = phrase.id in VoiceRuntime.phraseIds,
                    isTelevision = isTelevision,
                    onCheckedChange = { on ->
                        val next = if (on) VoiceRuntime.phraseIds + phrase.id else VoiceRuntime.phraseIds - phrase.id
                        if (next.isNotEmpty()) {
                            VoiceRuntime.phraseIds = next
                            saveVoicePhraseIds(context, next)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text("Readout", color = TextBright, fontSize = CineType.Body, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            val stats = VoiceRuntime.stats
            val status = when {
                !VoiceRuntime.enabled -> "Voice is off."
                VoiceRuntime.isListening -> "Listening now."
                VoiceRuntime.lastError != null -> "Couldn't start: ${VoiceRuntime.lastError}"
                else -> "Waiting. It listens when CineVault is open and the screen is on."
            }
            Text(status, color = if (VoiceRuntime.lastError != null && !VoiceRuntime.isListening) GelRose else TextMuted, fontSize = CineType.Label)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Listened: ${formatListened(stats.listenedMs)}", color = TextMuted, fontSize = CineType.Label)
            Text("Speech-like sound present: ${formatPercent(stats.speechShare())} of that time", color = TextMuted, fontSize = CineType.Label)
            Text("Wake-word work: ${formatPercent(stats.processingShare())} of one processor core", color = TextMuted, fontSize = CineType.Label)
            Text("Wake word heard: ${stats.totalHears} times", color = TextMuted, fontSize = CineType.Label)
            Text("Confirmed by the speech check: ${stats.confirmed}, turned away: ${stats.rejected}", color = TextMuted, fontSize = CineType.Label)
            VoiceWakePhrase.values().filter { stats.hearsFor(it.id) > 0 }.forEach { phrase ->
                Text("   ${phrase.label}: ${stats.hearsFor(phrase.id)}", color = TextFaint, fontSize = CineType.Caption)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TvFocusableSlot(
                isTelevision = isTelevision,
                shape = RoundedCornerShape(50),
                onActivate = { stats.reset(); VoiceRuntime.statsVersion++ }
            ) {
                CineButton(
                    text = "Reset readout",
                    onClick = { stats.reset(); VoiceRuntime.statsVersion++ },
                    style = CineButtonStyle.Secondary
                )
            }
        }
    }

    val resetSection: @Composable () -> Unit = {
        GlassSectionCard(
            title = "Reset Settings",
            subtitle = "Restore CineVault preferences to their defaults.",
            icon = Icons.Rounded.RestartAlt,
            accent = AccentReset
        ) {
            val onResetClick = { showResetSettingsConfirm = true }
            TvFocusableSlot(isTelevision = isTelevision, onActivate = onResetClick) {
                GlassActionRow(
                    icon = Icons.Rounded.RestartAlt,
                    iconTint = AccentReset,
                    title = "Reset CineVault settings",
                    subtitle = "Keeps your library, history, favourites, folders and subtitle files",
                    action = "RESET",
                    onClick = onResetClick
                )
            }
        }
    }

    val supportSection: @Composable () -> Unit = {
        GlassSectionCard(title = "Support CineVault", subtitle = "A small thank you keeps the vault alive.", icon = Icons.Filled.Favorite, accent = AccentSupport) {
            val onCoffeeClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/"))) }
            TvFocusableSlot(isTelevision = isTelevision, onActivate = onCoffeeClick) {
                GlassActionRow(icon = Icons.Filled.Favorite, iconTint = AccentSupport, title = "Buy me a coffee", subtitle = "Optional support / donate button", action = "♥", onClick = onCoffeeClick)
            }
        }
    }

    val aboutSection: @Composable () -> Unit = {
        GlassSectionCard(title = "About", subtitle = "Premium local cinema experience.", icon = Icons.Filled.Info, accent = AccentAbout) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = AccentAbout, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "CineVault v${appVersionName(context)}", color = TextBright, fontSize = CineType.Body, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your personal cinema, built from the ground up. Play straight from local storage, a USB drive, or a NAS over SMB, with real decoding for DTS, TrueHD and the formats most players choke on. TMDB and OMDB automatically bring in posters, cast, genres, collections, and IMDb/Rotten Tomatoes ratings for everything you own. A cinematic glass-and-amber design throughout, gesture-driven playback, and a private Select Folder space that stays exactly that.",
                color = TextMuted, fontSize = CineType.Label, lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            TvFocusableSlot(isTelevision = isTelevision, onActivate = { showCrashLog = true }) {
                GlassActionRow(
                    icon = Icons.Filled.Info, iconTint = AccentAbout,
                    title = "View Crash Log", subtitle = "Copy it or share it with the developer",
                    action = "OPEN"
                ) { showCrashLog = true }
            }
        }
    }

    val sectionsByGroup: Map<SettingsGroup, List<@Composable () -> Unit>> = mapOf(
        SettingsGroup.Library to listOf(foldersSection, libraryToolsSection, secretHelpSection),
        SettingsGroup.Network to listOf(networkSection, streamSection),
        SettingsGroup.Playback to listOf(glassesSection, playerPointerSection, voiceSection),
        SettingsGroup.Tutorial to listOf(tutorialSection),
        SettingsGroup.You to listOf(quickSwitchesSection, nameSection, resetSection, supportSection, aboutSection)
    )
    val phoneOrder: List<@Composable () -> Unit> = listOf(
        quickSwitchesSection, nameSection, tutorialSection, networkSection, streamSection,
        foldersSection, libraryToolsSection, secretHelpSection, glassesSection, playerPointerSection, voiceSection,
        resetSection, supportSection, aboutSection
    )

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize().then(tvKeys)) {
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Settings", color = TextBright, fontFamily = NewsreaderFamily, fontSize = CineType.Display)
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsGroup.values().forEach { group ->
                        SettingsGroupItem(
                            group = group,
                            selected = group == selectedGroup,
                            onClick = { selectedGroup = group }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, top = 20.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    sectionsByGroup[selectedGroup].orEmpty().forEach { section -> section() }
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .then(tvKeys),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HeroCard()
                    phoneOrder.forEach { section -> section() }
                    SignatureFooter()
                    // Room for the floating dock.
                    Spacer(modifier = Modifier.height(110.dp))
                }
            }
        }

        // Stream URL dialog
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

        // Crash log: Copy and Share keep it open. Clear asks twice.
        if (showCrashLog) {
            val logText = remember(showCrashLog) { readCrashLog(context) }
            var confirmClear by remember(showCrashLog) { mutableStateOf(false) }
            val closeLogFocusRequester = remember { FocusRequester() }
            if (isTelevision) {
                LaunchedEffect(showCrashLog) {
                    closeLogFocusRequester.requestFocus()
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)).clickable { showCrashLog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 560.dp)
                        .heightIn(max = 520.dp)
                        .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .clickable(enabled = false) { }
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Crash Log", color = TextBright, fontSize = CineType.Title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        CineCloseButton(onClick = { showCrashLog = false })
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text(
                            text = logText.ifBlank { "No crashes logged yet." },
                            color = TextMuted, fontSize = CineType.Caption, lineHeight = 16.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    val onCopyClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("CineVault Crash Log", logText.ifBlank { "No crashes logged yet." }))
                        Toast.makeText(context, "Crash log copied", Toast.LENGTH_SHORT).show()
                    }
                    val onShareClick = { shareCrashReport(context, logText) }
                    val onClearLogClick = {
                        if (confirmClear) {
                            clearCrashLog(context)
                            showCrashLog = false
                        } else {
                            confirmClear = true
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.then(
                            if (isTelevision) {
                                Modifier.onKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                                        Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                                        else -> false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                    ) {
                        TvFocusableSlot(isTelevision = isTelevision, focusRequester = closeLogFocusRequester, shape = RoundedCornerShape(50), onActivate = onShareClick) {
                            CineButton(text = "Share", onClick = onShareClick)
                        }
                        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = onCopyClick) {
                            CineButton(text = "Copy", onClick = onCopyClick, style = CineButtonStyle.Secondary)
                        }
                        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = onClearLogClick) {
                            CineButton(
                                text = if (confirmClear) "Tap again to clear" else "Clear log",
                                onClick = onClearLogClick,
                                style = CineButtonStyle.Danger
                            )
                        }
                    }
                }
            }
        }

        // Reset: risky, so buttons only. A stray tap outside does nothing.
        if (showResetSettingsConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 360.dp)
                        .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .padding(20.dp)
                ) {
                    Text(text = "Reset CineVault settings?", color = TextBright, fontSize = CineType.Title, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Subtitle, audio, glasses and display preferences, plus the Fetch online metadata and New collection page switches, will return to defaults. Your library, watch history, favourites, selected folders, your name, downloaded subtitles and AI subtitles stay untouched.",
                        color = TextMuted, fontSize = CineType.Label, lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    val onCancelReset = { showResetSettingsConfirm = false }
                    val onConfirmReset = {
                        resetCineVaultSettings(context)
                        // Re-read so the switches show their new state at once.
                        metadataFetchEnabled = loadMetadataFetchEnabled(context)
                        collectionPageV2 = loadCollectionPageV2Enabled(context)
                        VoiceRuntime.loadFrom(context)
                        showResetSettingsConfirm = false
                        Toast.makeText(context, "CineVault settings reset", Toast.LENGTH_SHORT).show()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = onCancelReset) {
                            CineButton(text = "Cancel", onClick = onCancelReset, style = CineButtonStyle.Secondary)
                        }
                        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = onConfirmReset) {
                            CineButton(text = "Reset", onClick = onConfirmReset, style = CineButtonStyle.Danger)
                        }
                    }
                }
            }
        }

        // Folder removal: risky, so buttons only.
        val target = folderPendingRemoval
        if (target != null) {
            val cancelFocusRequester = remember { FocusRequester() }
            if (isTelevision) {
                LaunchedEffect(target.id) {
                    cancelFocusRequester.requestFocus()
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 340.dp)
                        .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .padding(20.dp)
                ) {
                    Text(text = "Remove this folder?", color = TextBright, fontSize = CineType.Title, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${target.displayName}\" will be removed from Select folders. The files themselves aren't touched.",
                        color = TextMuted, fontSize = CineType.Label, lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    val onCancelClick = { folderPendingRemoval = null }
                    val onRemoveClick = {
                        removeRestrictedFolder(context, target.id)
                        restrictedFolders = loadRestrictedFolders(context)
                        folderPendingRemoval = null
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.then(
                            if (isTelevision) {
                                Modifier.onKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                                        Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                                        else -> false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                    ) {
                        TvFocusableSlot(isTelevision = isTelevision, focusRequester = cancelFocusRequester, shape = RoundedCornerShape(50), onActivate = onCancelClick) {
                            CineButton(text = "Cancel", onClick = onCancelClick, style = CineButtonStyle.Secondary)
                        }
                        TvFocusableSlot(isTelevision = isTelevision, shape = RoundedCornerShape(50), onActivate = onRemoveClick) {
                            CineButton(text = "Remove", onClick = onRemoveClick, style = CineButtonStyle.Danger)
                        }
                    }
                }
            }
        }

        if (showNameDialog) {
            SettingsNameDialog(
                initial = displayName,
                onSave = { raw ->
                    saveDisplayName(context, raw)
                    displayName = loadDisplayName(context)
                    showNameDialog = false
                },
                onDismiss = { showNameDialog = false }
            )
        }

        if (showTutorial) {
            TutorialHubScreen(
                onClose = { showTutorial = false },
                onTry = { destination ->
                    showTutorial = false
                    when (destination) {
                        TutorialTry.GlassesPractice -> onOpenGlassesGestureTutorial()
                        TutorialTry.NetworkHub -> onOpenNetworkHub()
                        TutorialTry.None -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    isTelevision: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextBright, fontSize = CineType.Body, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = TextMuted, fontSize = CineType.Caption, lineHeight = 17.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        TvFocusableSlot(
            isTelevision = isTelevision,
            shape = RoundedCornerShape(50),
            onActivate = { onCheckedChange(!checked) }
        ) {
            CineToggle(checked = checked, onCheckedChange = onCheckedChange, showLabel = true)
        }
    }
}

@Composable
private fun SettingsGroupItem(group: SettingsGroup, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .cineCard(radius = 26.dp, gel = if (selected) group.gel else null)
            .background(if (selected) group.gel.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = group.icon, contentDescription = null, tint = group.gel, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = group.title,
            color = if (selected) group.gel else TextBright,
            fontSize = CineType.Body,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * "Your name" window. Typing never loses words: a tap outside only closes it
 * when nothing has been changed. The close button is always top right.
 */
@Composable
private fun SettingsNameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable { if (text == initial) onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 360.dp)
                .glassPanel(cornerRadius = 24.dp, fill = SpaceMid.copy(alpha = 0.98f))
                .clickable(enabled = false) { }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Your name", color = TextBright, fontSize = CineType.Title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CineCloseButton(onClick = onDismiss)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Shown in the Home greeting. Leave it empty for no name.",
                color = TextMuted, fontSize = CineType.Label, lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_DISPLAY_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("Your first name") },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AmberCore,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = AmberCore
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            CineButton(text = "Save", onClick = { onSave(text) })
        }
    }
}
