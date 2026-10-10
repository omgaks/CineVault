package com.sole.cinevault

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay

/**
 * First-run flow: Welcome (optional name) -> Scan -> Feature reel.
 * Skip is always visible top right. Back goes one step back, and on the first
 * step it skips. Whatever was typed in the name box is saved whenever the
 * flow ends or moves on, so it is never lost.
 */
@Composable
internal fun OnboardingScreen(
    onVideosLoaded: (List<VideoWithMetadata>) -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    var name by remember { mutableStateOf(loadDisplayName(context)) }

    fun finish() {
        saveDisplayName(context, name)
        markOnboardingDone(context)
        onFinished()
    }

    BackHandler {
        val previous = step.previous
        if (previous != null) step = previous else finish()
    }

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack).safeDrawingPadding()) {
        // Skip: always visible, top right.
        Text(
            text = "Skip",
            color = TextMuted,
            fontSize = CineType.Label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { finish() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = "Skip the welcome" }
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StepDots(step)
            when (step) {
                OnboardingStep.Welcome -> WelcomeStep(
                    name = name,
                    onNameChange = { name = sanitizeNameInput(it) },
                    onContinue = { saveDisplayName(context, name); step = OnboardingStep.Scan }
                )
                OnboardingStep.Scan -> ScanStep(
                    onVideosLoaded = onVideosLoaded,
                    onContinue = { step = OnboardingStep.Reel }
                )
                OnboardingStep.Reel -> ReelStep(onDone = { finish() })
            }
        }
    }
}

/** Allows typing freely (spaces included) but never past the limit. */
private fun sanitizeNameInput(raw: String): String = raw.take(MAX_DISPLAY_NAME_LENGTH)

@Composable
private fun StepDots(step: OnboardingStep) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "Step ${step.number} of 3" }
    ) {
        OnboardingStep.values().forEach { s ->
            val active = s == step
            Box(
                modifier = Modifier
                    .size(width = if (active) 28.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) GelGold else Color.White.copy(alpha = 0.18f))
            )
        }
        Spacer(Modifier.size(4.dp))
        Text("Step ${step.number} of 3", color = TextFaint, fontSize = CineType.Caption)
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = TextBright,
            fontFamily = NewsreaderFamily,
            fontSize = CineType.Display
        )
        Text(text = body, color = TextMuted, fontSize = CineType.Body)
    }
}

@Composable
private fun WelcomeStep(name: String, onNameChange: (String) -> Unit, onContinue: () -> Unit) {
    Heading(
        title = "Welcome to CineVault",
        body = "Your own films, in one beautiful place. Everything stays on this device."
    )
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        label = { Text("What should we call you? (optional)", color = TextFaint) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextBright,
            unfocusedTextColor = TextBright,
            focusedContainerColor = GlassSurfaceStrong,
            unfocusedContainerColor = GlassSurface,
            focusedBorderColor = GelGold.copy(alpha = 0.65f),
            unfocusedBorderColor = GlassBorderTop,
            cursorColor = GelGold
        )
    )
    Text(
        text = "Used only for the greeting on Home. You can change it any time in Settings.",
        color = TextFaint,
        fontSize = CineType.Caption
    )
    CineButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ScanStep(onVideosLoaded: (List<VideoWithMetadata>) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val permission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var denied by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            denied = false
            started = true
            LibraryScanController.start(context, onVideosLoaded)
        } else {
            denied = true
        }
    }
    Heading(
        title = "Find your films",
        body = "CineVault can look for videos on this device. It only reads them. Nothing is uploaded, moved or changed."
    )
    if (LibraryScanController.isScanning) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = LibraryScanController.status.ifBlank { "Scanning..." },
                color = TextMuted,
                fontSize = CineType.Label
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AmberCore,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text(
                text = "You can carry on. The scan keeps going in the background.",
                color = TextFaint,
                fontSize = CineType.Caption
            )
        }
    } else if (started) {
        Text("Scan finished. Your films will appear in Library and Home.", color = GelMint, fontSize = CineType.Label)
    }
    if (denied) {
        Text(
            text = "No problem. Without permission CineVault can't see your videos yet. You can allow it later from Library, or pick folders and network shares there instead.",
            color = TextMuted,
            fontSize = CineType.Label
        )
    }
    if (!LibraryScanController.isScanning && !started) {
        CineButton(text = "Scan this device", onClick = { launcher.launch(permission) }, modifier = Modifier.fillMaxWidth())
    }
    CineButton(
        text = if (LibraryScanController.isScanning || started) "Continue" else "Not now",
        onClick = onContinue,
        style = if (LibraryScanController.isScanning || started) CineButtonStyle.Primary else CineButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = "Folders and network shares can be added later in Library.",
        color = TextFaint,
        fontSize = CineType.Caption
    )
}

@Composable
private fun ReelStep(onDone: () -> Unit) {
    val guides = remember { TUTORIAL_GUIDES.filter { !it.comingSoon } }
    var tick by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(paused) {
        while (!paused) {
            delay(2600)
            tick++
        }
    }
    val index = reelIndexAt(tick, guides.size)
    val current = guides[index]

    Heading(
        title = "Here's what's inside",
        body = "A few things worth knowing. Tap any chip to hold it, and find full guides any time in Settings."
    )
    // The pill: one feature at a time.
    Crossfade(targetState = current, label = "reel") { guide ->
        val g = TutorialGels[guide.gelIndex % TutorialGels.size]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cineCard(radius = 26.dp, gel = g)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(guide.title, color = g, fontFamily = NewsreaderFamily, fontSize = CineType.Title)
            Text(guide.tagline, color = TextBright, fontSize = CineType.Body, textAlign = TextAlign.Start)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        guides.forEachIndexed { i, guide ->
            CineChip(
                text = guide.title,
                selected = i == index,
                onClick = { tick = i; paused = true },
                gel = TutorialGels[guide.gelIndex % TutorialGels.size]
            )
        }
    }
    CineButton(text = "Start watching", onClick = onDone, modifier = Modifier.fillMaxWidth())
}
