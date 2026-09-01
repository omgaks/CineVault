package com.sole.cinevault

import com.sole.cinevault.library.*

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

// FEATURE: fresh-install empty-state redesign. Iterated twice — phase 1
// (structure + responsiveness) and this pass (visual direction: header
// row, bigger bare logo, rating badges, single-row neon-styled tiles,
// landscape-specific compacting so nothing needs to scroll on a short
// phone-landscape screen). Choose Folder / Connect SMB / Open Stream
// still all navigate to the Library tab for now — those entry points
// live inside LocalVideoLibraryScreen's own internal state, not exposed
// as callbacks HomeScreen can reach directly yet.
@Immutable
private data class WelcomeDimensions(
    val horizontalPadding: Dp,
    val heroSize: Dp,
    val headingSize: androidx.compose.ui.unit.TextUnit,
    val taglineSize: androidx.compose.ui.unit.TextUnit,
    val bodySize: androidx.compose.ui.unit.TextUnit,
    val primaryButtonHeight: Dp,
    val sourceTileHeight: Dp,
    val tileIconSize: Dp,
    val contentMaxWidth: Dp,
    val sectionGap: Dp
)

@Composable
private fun rememberWelcomeDimensions(isTablet: Boolean, isLandscape: Boolean, maxHeight: Dp): WelcomeDimensions =
    remember(isTablet, isLandscape, maxHeight) {
        val base = when {
            isTablet && isLandscape -> WelcomeDimensions(
                horizontalPadding = 56.dp, heroSize = 84.dp,
                headingSize = 20.sp, taglineSize = 11.sp, bodySize = 10.sp,
                primaryButtonHeight = 40.dp, sourceTileHeight = 42.dp, tileIconSize = 24.dp,
                contentMaxWidth = 680.dp, sectionGap = 6.dp
            )
            isTablet -> WelcomeDimensions(
                horizontalPadding = 40.dp, heroSize = 120.dp,
                headingSize = 24.sp, taglineSize = 13.sp, bodySize = 12.sp,
                primaryButtonHeight = 48.dp, sourceTileHeight = 50.dp, tileIconSize = 28.dp,
                contentMaxWidth = 620.dp, sectionGap = 8.dp
            )
            isLandscape -> WelcomeDimensions(
                horizontalPadding = 20.dp, heroSize = 48.dp,
                headingSize = 13.sp, taglineSize = 9.sp, bodySize = 9.sp,
                primaryButtonHeight = 32.dp, sourceTileHeight = 32.dp, tileIconSize = 18.dp,
                contentMaxWidth = 460.dp, sectionGap = 3.dp
            )
            else -> WelcomeDimensions(
                horizontalPadding = 20.dp, heroSize = 90.dp,
                headingSize = 19.sp, taglineSize = 12.sp, bodySize = 11.sp,
                primaryButtonHeight = 44.dp, sourceTileHeight = 48.dp, tileIconSize = 26.dp,
                contentMaxWidth = 460.dp, sectionGap = 6.dp
            )
        }
        // FIX: landscape was still reported cut off after the first
        // height-fix pass. Two changes here specifically address that:
        // 1. Subtracting a fixed buffer up front for space this content
        //    never actually gets — the outer Column's own vertical
        //    padding (20dp) plus the LazyColumn's bottom contentPadding
        //    (30dp) were previously invisible to this comparison, making
        //    it think there was ~50dp more room than there really was.
        // 2. A wider, more aggressive shrink range (down to 0.4x instead
        //    of 0.6x) and a lower trigger threshold, so tight tablet-
        //    landscape heights actually get compacted rather than assumed
        //    to already fit.
        val usableHeight = maxHeight - 50.dp
        val comfortableHeight = if (isTablet) 560.dp else 520.dp
        if (usableHeight >= comfortableHeight) base else {
            val shrink = (usableHeight / comfortableHeight).coerceIn(0.4f, 1f)
            base.copy(
                heroSize = (base.heroSize.value * shrink).dp.coerceAtLeast(36.dp),
                sectionGap = (base.sectionGap.value * shrink).dp.coerceAtLeast(2.dp),
                sourceTileHeight = (base.sourceTileHeight.value * shrink).dp.coerceAtLeast(30.dp)
            )
        }
    }

@Composable
private fun AmberSourceTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    height: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = height)
            .shadow(elevation = 6.dp, shape = shape, ambientColor = AmberGlow.copy(alpha = 0.3f), spotColor = AmberGlow.copy(alpha = 0.3f))
            .clip(shape)
            .background(GlassSurfaceStrong)
            .background(Brush.verticalGradient(0f to GlassHighlight, 0.45f to Color.Transparent, 1f to Color.Transparent))
            .border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.65f), AmberDeep.copy(alpha = 0.25f))), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(iconSize))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(text = subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun FreshInstallWelcomeContent(
    availableHeight: Dp,
    onScanLibrary: () -> Unit,
    onChooseFolder: () -> Unit,
    onConnectSmb: () -> Unit,
    onOpenStream: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // FIX: isTablet/isLandscapeMode used to compare against this
        // Box's own maxHeight — unreliable here since this composable
        // sits inside a LazyColumn item (see HomeScreen's outer
        // BoxWithConstraints for why). Width from this inner Box is
        // still fine (LazyColumn items ARE width-bound by
        // fillMaxWidth()); only height needed to come from the real,
        // passed-in screen measurement instead.
        val isTablet = minOf(maxWidth, availableHeight) >= 600.dp
        val isLandscapeMode = maxWidth > availableHeight
        val dims = rememberWelcomeDimensions(isTablet, isLandscapeMode, availableHeight)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = dims.contentMaxWidth)
                .align(Alignment.Center)
                .padding(horizontal = dims.horizontalPadding, vertical = 10.dp)
        ) {
            // FIX: was a Row with logo+wordmark pinned left and the privacy
            // chip pinned right — the one part of this screen that wasn't
            // center-aligned. Logo removed entirely (redundant with the
            // much larger hero logo right below), so there's nothing left
            // needing a left/right split — just the privacy chip, centered
            // like everything else.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(GlassSurface)
                        .border(1.dp, AmberCore.copy(alpha = 0.32f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = AmberCore, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Private · Local-first", color = TextBright, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(dims.sectionGap))

            // Everything below the header is center-aligned, as requested.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bare logo — no glow, no background box, just the round
                // logo itself, bigger and moved up (i.e. immediately below
                // the header rather than deep in the middle of the screen).
                // FIX: clipped to CircleShape — the source PNG's own square
                // canvas was showing as a visible dark square behind the
                // round artwork. Cropping to an inscribed circle hides the
                // square corners while keeping the circular logo art intact.
                Image(
                    painter = painterResource(id = R.drawable.cinevault_circle_logo),
                    contentDescription = "CineVault",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(dims.heroSize).clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(dims.sectionGap))

                Text(
                    text = "Welcome to CineVault",
                    color = TextBright, fontSize = dims.headingSize, fontWeight = FontWeight.Bold,
                    fontFamily = CinzelFontFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Turn your videos into a personal cinema.",
                    color = AmberCore.copy(alpha = 0.85f), fontSize = dims.taglineSize, fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                // FIX: kept to one line as requested — was wrapping across
                // two lines before. Ellipsis is the safety valve on the
                // narrowest phones rather than silently wrapping anyway.
                Text(
                    text = "CineVault finds movies and shows on this device, then adds artwork, ratings and details.",
                    color = TextBright.copy(alpha = 0.70f), fontSize = dims.bodySize,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Rating-source badges — text-only, no TMDB/IMDb/RT logo
                // graphics (those are trademarked; a personal project
                // reusing them isn't something to do without you
                // knowing). Matches the amber-pill-plus-star style
                // confirmed in preview.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("TMDB", "IMDb", "RT").forEach { label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AmberGlow.copy(alpha = 0.12f))
                                .border(1.dp, AmberCore.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 11.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = AmberCore, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dims.sectionGap + 4.dp))

                // FIX: was a solid amber gradient fill with a black icon —
                // matches this app's OTHER amber-filled pills (like the
                // lock button), but not the play button specifically,
                // which is glass + radial glow + gradient border rather
                // than a flat fill. Replicating that exact recipe here
                // since that's what was actually asked for.
                val glow = rememberInfiniteTransition(label = "scanPillGlow")
                val glowAlpha by glow.animateFloat(
                    initialValue = 0.45f, targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                    label = "scanPillGlowAlpha"
                )
                val density = LocalDensity.current
                val glowRadiusPx = with(density) { (dims.primaryButtonHeight.value * 1.6f).dp.toPx() }.coerceAtLeast(1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(dims.primaryButtonHeight)
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurfaceStrong)
                        .background(Brush.verticalGradient(0f to GlassHighlight, 0.45f to Color.Transparent, 1f to Color.Transparent))
                        .background(Brush.radialGradient(colors = listOf(AmberGlow.copy(alpha = glowAlpha * 0.5f), Color.Transparent), radius = glowRadiusPx))
                        .border(
                            width = 1.4.dp,
                            brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.75f + 0.2f * glowAlpha), AmberDeep.copy(alpha = 0.30f))),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onScanLibrary() }
                        .padding(horizontal = 22.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = AmberCore, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(text = "Scan My Library", color = TextBright, fontSize = if (isTablet) 18.sp else 16.sp, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Usually takes less than a minute", color = TextMuted, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(dims.sectionGap))

                // Single row on both orientations, as requested — Connect
                // SMB in the center, directly under the Scan button.
                // Compact, sized to content rather than stretched to fill
                // the row — amber/glass theme throughout, matching
                // FrostedPlayButton's actual recipe rather than a flat
                // color fill. Row itself isn't fillMaxWidth, so it centers
                // as a unit within the parent's centered column instead of
                // spanning edge to edge.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmberSourceTile(
                        icon = Icons.Filled.Link, title = "Open Stream", subtitle = "Play a direct video link",
                        height = dims.sourceTileHeight, iconSize = dims.tileIconSize, onClick = onOpenStream
                    )
                    AmberSourceTile(
                        icon = Icons.Filled.Dns, title = "Connect SMB", subtitle = "Add a computer or NAS",
                        height = dims.sourceTileHeight, iconSize = dims.tileIconSize, onClick = onConnectSmb
                    )
                    AmberSourceTile(
                        icon = Icons.Filled.Folder, title = "Choose Folder", subtitle = "Scan only what you select",
                        height = dims.sourceTileHeight, iconSize = dims.tileIconSize, onClick = onChooseFolder
                    )
                }

                Spacer(modifier = Modifier.height(dims.sectionGap))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = AmberCore, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "No account · No ads · Your videos stay on your device",
                        color = TextMuted, fontSize = 12.sp, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

