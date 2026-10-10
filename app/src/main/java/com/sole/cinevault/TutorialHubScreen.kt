package com.sole.cinevault

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

internal val TutorialGels = listOf(GelGold, GelSky, GelViolet, GelRose, GelMint, GelCoral)

private const val TUTORIAL_PREFS = "cinevault_tutorial"
private const val KEY_SEEN = "seen_guides"

private fun loadSeenGuides(context: Context): Set<String> =
    context.getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_SEEN, emptySet())?.toSet() ?: emptySet()

private fun markGuideSeen(context: Context, id: String) {
    val updated = loadSeenGuides(context) + id
    context.getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_SEEN, updated).apply()
}

/**
 * Tutorial hub: a list of guides, each opening into its own page.
 * Back closes the open guide first, then the hub. The close button is always
 * top right.
 */
@Composable
internal fun TutorialHubScreen(
    onClose: () -> Unit,
    onTry: (TutorialTry) -> Unit
) {
    val context = LocalContext.current
    var openGuideId by remember { mutableStateOf<String?>(null) }
    var seen by remember { mutableStateOf(loadSeenGuides(context)) }
    val openGuide = TUTORIAL_GUIDES.firstOrNull { it.id == openGuideId }

    BackHandler(enabled = true) {
        if (openGuide != null) openGuideId = null else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            // Swallow touches so nothing behind the hub reacts.
            .clickable(enabled = false) { }
    ) {
        if (openGuide == null) {
            TutorialGuideList(
                seen = seen,
                onOpen = { guide ->
                    markGuideSeen(context, guide.id)
                    seen = loadSeenGuides(context)
                    openGuideId = guide.id
                },
                onClose = onClose
            )
        } else {
            TutorialGuidePage(
                guide = openGuide,
                onClose = { openGuideId = null },
                onTry = { target ->
                    openGuideId = null
                    onTry(target)
                }
            )
        }
    }
}

@Composable
private fun TutorialGuideList(
    seen: Set<String>,
    onOpen: (TutorialGuide) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LEARN CINEVAULT",
                    color = AmberCore,
                    fontFamily = PlexMonoFamily,
                    fontSize = CineType.Caption,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = "Tutorial",
                    color = TextBright,
                    fontFamily = NewsreaderFamily,
                    fontSize = CineType.Display
                )
                Text(
                    text = "${seen.size} of ${TUTORIAL_GUIDES.size} guides opened",
                    color = TextMuted,
                    fontSize = CineType.Label
                )
            }
            CineCloseButton(onClick = onClose)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            items(TUTORIAL_GUIDES, key = { it.id }) { guide ->
                val gel = TutorialGels[guide.gelIndex % TutorialGels.size]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cineCard(radius = 24.dp, gel = gel)
                        .clickable { onOpen(guide) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = guide.title,
                            color = TextBright,
                            fontFamily = NewsreaderFamily,
                            fontSize = CineType.Title,
                            modifier = Modifier.weight(1f)
                        )
                        if (guide.comingSoon) {
                            CineBadge(text = "SOON", gel = gel)
                        } else if (guide.id in seen) {
                            CineBadge(text = "SEEN", gel = GelMint)
                        }
                    }
                    Text(text = guide.tagline, color = TextMuted, fontSize = CineType.Label)
                }
            }
        }
    }
}

@Composable
private fun TutorialGuidePage(
    guide: TutorialGuide,
    onClose: () -> Unit,
    onTry: (TutorialTry) -> Unit
) {
    val gel = TutorialGels[guide.gelIndex % TutorialGels.size]
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GUIDE",
                    color = gel,
                    fontFamily = PlexMonoFamily,
                    fontSize = CineType.Caption,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = guide.title,
                    color = TextBright,
                    fontFamily = NewsreaderFamily,
                    fontSize = CineType.Display
                )
            }
            CineCloseButton(onClick = onClose)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = guide.tagline, color = TextMuted, fontSize = CineType.Body)

            if (guide.id == "glasses") {
                GlassesNodShakeExplainer(gel = gel)
            }

            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                guide.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth().cineCard(radius = 18.dp).padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = gel,
                            fontFamily = PlexMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = CineType.Label,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(text = step, color = TextBright, fontSize = CineType.Body, lineHeight = 21.sp)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).cineCard(radius = 18.dp, gel = gel).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "GOOD TO KNOW",
                    color = gel,
                    fontFamily = PlexMonoFamily,
                    fontSize = CineType.Caption,
                    letterSpacing = 1.4.sp
                )
                Text(text = guide.tip, color = TextBright, fontSize = CineType.Label, lineHeight = 19.sp)
            }

            if (guide.tryIt != TutorialTry.None) {
                CineButton(
                    text = "Try it now",
                    onClick = { onTry(guide.tryIt) }
                )
            }
        }
    }
}

/**
 * A small looping animation, drawn in the app: a head with glasses nods
 * (up and down) and then shakes (side to side). No video file needed.
 */
@Composable
private fun GlassesNodShakeExplainer(gel: Color) {
    val transition = rememberInfiniteTransition(label = "glasses_explainer")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "glasses_explainer_t"
    )
    val nodding = t < 0.5f
    val phase = if (nodding) t / 0.5f else (t - 0.5f) / 0.5f
    val swing = kotlin.math.sin(phase * 2f * Math.PI.toFloat() * 2f)

    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).cineCard(radius = 24.dp, gel = gel).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 160.dp, height = 140.dp)) {
            val travel = 14.dp.toPx()
            val dx = if (nodding) 0f else swing * travel
            val dy = if (nodding) swing * travel else 0f
            val centre = Offset(size.width / 2f + dx, size.height / 2f + dy)
            val headRadius = 48.dp.toPx()
            drawCircle(color = Color(0xFF1E2430), radius = headRadius, center = centre)
            drawCircle(color = gel.copy(alpha = 0.5f), radius = headRadius, center = centre, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            val lensW = 30.dp.toPx()
            val lensH = 20.dp.toPx()
            val lensY = centre.y - 8.dp.toPx()
            drawRoundRect(color = gel, topLeft = Offset(centre.x - lensW - 4.dp.toPx(), lensY), size = Size(lensW, lensH), cornerRadius = CornerRadius(8.dp.toPx()))
            drawRoundRect(color = gel, topLeft = Offset(centre.x + 4.dp.toPx(), lensY), size = Size(lensW, lensH), cornerRadius = CornerRadius(8.dp.toPx()))
        }
        Text(
            text = if (nodding) "NOD: confirm, or play and pause" else "SHAKE: dismiss, or show the controls",
            color = gel,
            fontFamily = PlexMonoFamily,
            fontSize = CineType.Label
        )
    }
}
