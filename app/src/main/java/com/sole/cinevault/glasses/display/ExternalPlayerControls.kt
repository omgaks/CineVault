package com.sole.cinevault.glasses.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/**
 * What the glasses controls can ask the REAL player screen (on the tablet side of the app) to
 * do. VideoPlayerScreen fills these in; the glasses never own a second player, they send
 * commands to the one that exists. Play, pause, seek, speed and audio tracks go straight to
 * the shared ExoPlayer instead.
 */
internal object ExternalPlayerCommands {
    var back: (() -> Unit)? = null
    var next: (() -> Unit)? = null
    var previous: (() -> Unit)? = null
    var setSubtitles: ((Boolean) -> Unit)? = null
    var hasEpisodes by mutableStateOf(false)
    var subtitlesOn by mutableStateOf(false)

    /** Bumped on every glasses control press so the tablet does not auto-hide mid-use. */
    var interactionKey by mutableIntStateOf(0)

    fun touched() {
        interactionKey++
    }

    fun clear() {
        back = null
        next = null
        previous = null
        setSubtitles = null
        hasEpisodes = false
        subtitlesOn = false
    }
}

internal val GLASSES_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/** 0..1 across the seek bar to a film position. */
internal fun seekPositionMs(fraction: Float, durationMs: Long): Long =
    (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0L)).toLong()

private enum class Panel { AUDIO, SPEED }

@Composable
internal fun ExternalPlayerControls(player: Player, title: String, modifier: Modifier = Modifier) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            speed = player.playbackParameters.speed
            kotlinx.coroutines.delay(250)
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    val shownFraction = scrubFraction
        ?: if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(modifier = modifier.fillMaxSize()) {
        // Pointing at empty picture closes the controls (or an open panel first).
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) {
                if (panel != null) panel = null else ExternalPlayerHudState.requestHide()
            },
        )

        // Top: back + title
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", wide = true) {
                ExternalPlayerCommands.touched()
                ExternalPlayerCommands.back?.invoke()
            }
            Spacer(Modifier.width(20.dp))
            Text(title, color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        // Panels sit just above the bottom bar.
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 190.dp)) {
            when (panel) {
                Panel.SPEED -> OptionList("Speed", GLASSES_SPEEDS.map { speedLabel(it) to (it == speed) }) { i ->
                    ExternalPlayerCommands.touched()
                    player.setPlaybackSpeed(GLASSES_SPEEDS[i])
                    panel = null
                }
                Panel.AUDIO -> {
                    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
                    if (groups.isEmpty()) {
                        OptionList("Audio", listOf("Only one audio track" to true)) { panel = null }
                    } else {
                        OptionList(
                            "Audio",
                            groups.mapIndexed { i, g ->
                                val f = g.getTrackFormat(0)
                                val name = f.label ?: f.language?.uppercase() ?: "Track ${i + 1}"
                                name to g.isSelected
                            },
                        ) { i ->
                            ExternalPlayerCommands.touched()
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(groups[i].mediaTrackGroup, 0))
                                .build()
                            panel = null
                        }
                    }
                }
                null -> Unit
            }
        }

        // Bottom: seek bar + buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHudTime((shownFraction * duration).toLong()), color = TextBright, fontSize = 15.sp)
                Spacer(Modifier.width(14.dp))
                SeekBar(
                    fraction = shownFraction,
                    modifier = Modifier.weight(1f),
                    onScrub = { f ->
                        ExternalPlayerCommands.touched()
                        scrubFraction = f
                    },
                    onCommit = { f ->
                        ExternalPlayerCommands.touched()
                        player.seekTo(seekPositionMs(f, duration))
                        scrubFraction = null
                    },
                )
                Spacer(Modifier.width(14.dp))
                Text(formatHudTime(duration), color = TextMuted, fontSize = 15.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ExternalPlayerCommands.hasEpisodes) {
                    GlassButton(Icons.Rounded.SkipPrevious, "Previous") {
                        ExternalPlayerCommands.touched(); ExternalPlayerCommands.previous?.invoke()
                    }
                }
                GlassButton(Icons.Rounded.Replay10, "-10s") {
                    ExternalPlayerCommands.touched()
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                }
                GlassButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause" else "Play", wide = true, primary = true) {
                    ExternalPlayerCommands.touched()
                    if (player.isPlaying) player.pause() else player.play()
                }
                GlassButton(Icons.Rounded.Forward10, "+10s") {
                    ExternalPlayerCommands.touched()
                    val end = if (duration > 0L) duration else Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end))
                }
                if (ExternalPlayerCommands.hasEpisodes) {
                    GlassButton(Icons.Rounded.SkipNext, "Next") {
                        ExternalPlayerCommands.touched(); ExternalPlayerCommands.next?.invoke()
                    }
                }
                Spacer(Modifier.width(18.dp))
                GlassButton(
                    if (ExternalPlayerCommands.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                    if (ExternalPlayerCommands.subtitlesOn) "Subs on" else "Subs off",
                    wide = true,
                ) {
                    ExternalPlayerCommands.touched()
                    ExternalPlayerCommands.setSubtitles?.invoke(!ExternalPlayerCommands.subtitlesOn)
                }
                GlassButton(Icons.Rounded.Audiotrack, "Audio", wide = true) {
                    ExternalPlayerCommands.touched()
                    panel = if (panel == Panel.AUDIO) null else Panel.AUDIO
                }
                GlassButton(Icons.Rounded.Speed, speedLabel(speed), wide = true) {
                    ExternalPlayerCommands.touched()
                    panel = if (panel == Panel.SPEED) null else Panel.SPEED
                }
            }
        }
    }
}

@Composable
private fun GlassButton(
    icon: ImageVector,
    label: String,
    wide: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .widthIn(min = 56.dp)
            .clip(if (wide) RoundedCornerShape(50) else CircleShape)
            .background(if (primary) AmberCore else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 20.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (primary) Color.Black else TextBright, modifier = Modifier.size(28.dp))
        if (wide) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (primary) Color.Black else TextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OptionList(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6101018))
            .padding(18.dp)
            .widthIn(min = 260.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = TextMuted, fontSize = 14.sp)
        options.forEachIndexed { i, (label, selected) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) AmberCore.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
                    .clickable { onPick(i) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = if (selected) AmberCore else TextBright, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Tap or drag anywhere on the bar. Works with the Halo's synthetic touches. */
@Composable
private fun SeekBar(
    fraction: Float,
    modifier: Modifier,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    var f = (down.position.x / w).coerceIn(0f, 1f)
                    onScrub(f)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Main)
                        val c = e.changes.firstOrNull() ?: break
                        f = (c.position.x / w).coerceIn(0f, 1f)
                        if (!c.pressed) break
                        c.consume()
                        onScrub(f)
                    }
                    onCommit(f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.001f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(AmberCore))
    }
}
