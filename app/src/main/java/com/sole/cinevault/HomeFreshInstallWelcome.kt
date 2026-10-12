package com.sole.cinevault

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Home when the library is empty: the same Amber Lens look as the first-run
 * welcome. A serif headline, a feature ticker that cycles through what the app
 * can do, one clear Scan button and the three other ways to add films.
 * Nothing here is hidden behind a scroll on a normal phone or tablet.
 */
@Composable
internal fun FreshInstallWelcomeContent(
    @Suppress("UNUSED_PARAMETER") availableHeight: Dp,
    onScanLibrary: () -> Unit,
    onChooseFolder: () -> Unit,
    onConnectSmb: () -> Unit,
    onOpenStream: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CineBadge(text = "Private · Local-first", gel = AmberCore)

            Image(
                painter = painterResource(id = R.drawable.cinevault_circle_logo),
                contentDescription = "CineVault",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(if (wide) 96.dp else 84.dp).clip(CircleShape)
            )

            Text(
                text = "Welcome to CineVault",
                color = TextBright,
                fontFamily = NewsreaderFamily,
                fontSize = CineType.Display,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Turn your videos into a personal cinema. CineVault finds the movies and shows on this device, then adds artwork, ratings and details.",
                color = TextMuted,
                fontSize = CineType.Body,
                textAlign = TextAlign.Center
            )

            FeatureTicker()

            CineButton(
                text = "Scan My Library",
                onClick = onScanLibrary,
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
            )
            Text(text = "Usually takes less than a minute", color = TextFaint, fontSize = CineType.Caption)

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SourceCard(Icons.Filled.Link, "Open Stream", "Play a direct video link", GelSky, Modifier.weight(1f), onOpenStream)
                    SourceCard(Icons.Filled.Dns, "Connect SMB", "Add a computer or NAS", GelViolet, Modifier.weight(1f), onConnectSmb)
                    SourceCard(Icons.Filled.Folder, "Choose Folder", "Scan only what you select", GelMint, Modifier.weight(1f), onChooseFolder)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SourceCard(Icons.Filled.Link, "Open Stream", "Play a direct video link", GelSky, Modifier.fillMaxWidth(), onOpenStream)
                    SourceCard(Icons.Filled.Dns, "Connect SMB", "Add a computer or NAS", GelViolet, Modifier.fillMaxWidth(), onConnectSmb)
                    SourceCard(Icons.Filled.Folder, "Choose Folder", "Scan only what you select", GelMint, Modifier.fillMaxWidth(), onChooseFolder)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = AmberCore, modifier = Modifier.size(16.dp))
                Text(
                    text = "No account · No ads · Your videos stay on your device",
                    color = TextMuted,
                    fontSize = CineType.Caption,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** One feature at a time, changing every few seconds. Same guides as the Tutorial. */
@Composable
private fun FeatureTicker() {
    val guides = remember { TUTORIAL_GUIDES.filter { !it.comingSoon } }
    if (guides.isEmpty()) return
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            tick++
        }
    }
    val current = guides[reelIndexAt(tick, guides.size)]
    Crossfade(targetState = current, label = "welcomeTicker") { guide ->
        val g = TutorialGels[guide.gelIndex % TutorialGels.size]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cineCard(radius = 24.dp, gel = g)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(guide.title, color = g, fontFamily = NewsreaderFamily, fontSize = CineType.Title)
            Text(guide.tagline, color = TextBright, fontSize = CineType.Body)
        }
    }
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gel: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .cineCard(radius = 20.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(gel.copy(alpha = 0.16f)).border(1.dp, gel.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = gel, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, color = TextBright, fontSize = CineType.Body, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = CineType.Caption)
        }
    }
}
