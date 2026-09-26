package com.sole.cinevault.tvmode

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

/**
 * TV Home, phase 2 of native Android TV / Google TV support.
 *
 * What this is: a real, D-pad-navigable browse screen — every poster is
 * `focusable()`, scales up and shows an amber focus ring when the D-pad
 * moves onto it, and responds to DPAD_CENTER/ENTER, not just touch. Built
 * on plain Compose Foundation focus APIs rather than the separate
 * androidx.tv:tv-foundation library, deliberately, so this doesn't add a
 * new Gradle dependency to resolve before it even compiles once. This file
 * itself didn't need to change for phase 2 — MainActivity.kt now passes it
 * three real shelves (Continue Watching / Movies / TV Shows) instead of
 * phase 1's single placeholder shelf; TvHomeScreen only knows about
 * TvShelf/TvShelfItem, so it renders whatever shelves it's handed.
 *
 * What this is NOT yet, and the actual next step: because TV Home pushes
 * into the exact same VideoPlayerScreen the phone uses, resume position,
 * watch history, FFmpeg audio, and sidecar subtitle matching already work
 * on TV for free — no separate TV player, no forked VideoItem. What's
 * missing is D-pad/focus handling INSIDE that player itself: once a video
 * starts, a physical remote can't yet pause, seek, or skip, because
 * VideoPlayerScreen.kt's control surface is still touch-only. That's a
 * real, separate change to a 1,300-line file and is deliberately not
 * bundled into this phase — see the phase 3 plan before touching it.
 *
 * Bottom bar, Library/Search/Settings screens, and the Detail screen are
 * also still the touch-first phone UI underneath — a remote can reach Home
 * and start something, but can't yet browse everywhere else.
 */
@Composable
fun TvHomeScreen(
    shelves: List<TvShelf>,
    backdropUrl: String?,
    onScanRequest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SpaceDeep, SpaceBlack)
                )
            )
    ) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.28f }
            )
        }

        if (shelves.all { it.items.isEmpty() }) {
            TvEmptyLibraryState(onScanRequest)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 48.dp,
                    top = 40.dp,
                    bottom = 40.dp
                )
            ) {
                item {
                    Text(
                        text = "CineVault",
                        color = TextBright,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                itemsIndexed(shelves) { index, shelf ->
                    TvShelfRow(shelf, requestInitialFocus = index == 0)
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun TvShelfRow(shelf: TvShelf, requestInitialFocus: Boolean) {
    Column {
        Text(
            text = shelf.title,
            color = TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val firstItemFocusRequester = remember { FocusRequester() }

        // Compose never auto-focuses anything for a D-pad — without this,
        // the very first screen would render correctly but be completely
        // unreachable by remote control until something (touch) sets focus
        // first. Only the first shelf's first item claims initial focus, so
        // multiple shelves don't fight over it.
        if (requestInitialFocus && shelf.items.isNotEmpty()) {
            LaunchedEffect(Unit) {
                firstItemFocusRequester.requestFocus()
            }
        }

        LazyRow {
            items(shelf.items, key = { it.id }) { item ->
                val isFirst = item.id == shelf.items.firstOrNull()?.id
                TvPosterCard(
                    item = item,
                    focusRequester = if (isFirst) firstItemFocusRequester else null
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}

@Composable
private fun TvPosterCard(
    item: TvShelfItem,
    focusRequester: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.1f else 1f, label = "tvPosterScale")

    Column(
        modifier = Modifier.width(150.dp)
    ) {
        Box(
            modifier = Modifier
                .let { base -> if (focusRequester != null) base.focusRequester(focusRequester) else base }
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .glassPanel(cornerRadius = 10.dp)
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { keyEvent ->
                    val isActivate = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                    if (isActivate && keyEvent.type == KeyEventType.KeyUp) {
                        item.onClick()
                        true
                    } else {
                        false
                    }
                }
                .then(
                    if (focused) {
                        Modifier.background(AmberGlow.copy(alpha = 0.14f))
                    } else {
                        Modifier
                    }
                )
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(1).uppercase(),
                        color = TextMuted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = item.title,
            color = if (focused) TextBright else TextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun TvEmptyLibraryState(onScanRequest: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No videos yet",
                color = TextBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Scan a folder or SMB share from a phone or tablet running\nCineVault on the same network to get started.",
                color = TextMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        val isActivate = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                        if (isActivate && keyEvent.type == KeyEventType.KeyUp) {
                            onScanRequest()
                            true
                        } else {
                            false
                        }
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) AmberGlow else Color(0x33FFFFFF))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Scan Library",
                    color = if (focused) SpaceBlack else TextBright,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class TvShelf(
    val title: String,
    val items: List<TvShelfItem>
)

data class TvShelfItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val onClick: () -> Unit
)
