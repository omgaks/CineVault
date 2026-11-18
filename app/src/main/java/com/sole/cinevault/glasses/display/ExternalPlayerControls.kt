package com.sole.cinevault.glasses.display

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import com.sole.cinevault.FrostedPlayButton
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.CineType
import com.sole.cinevault.ui.theme.PlexMonoFamily
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
    val sizeContext = androidx.compose.ui.platform.LocalContext.current

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

        // Top: back + title on a soft scrim
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)))
                .padding(horizontal = 44.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GlassPill("Back", icon = Icons.AutoMirrored.Rounded.ArrowBack) {
                ExternalPlayerCommands.touched()
                ExternalPlayerCommands.back?.invoke()
            }
            Text(title, color = TextBright, fontSize = CineType.Title, fontWeight = FontWeight.Bold, maxLines = 1)
        }

        // Panels sit just above the bottom deck.
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 230.dp)) {
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

        // Bottom deck: seek thread, then the transport (with the real Lumen Ring play button),
        // then the option pills.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f))))
                .padding(horizontal = 44.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHudTime((shownFraction * duration).toLong()), color = TextBright, fontSize = CineType.Body, fontFamily = PlexMonoFamily)
                Spacer(Modifier.width(16.dp))
                GlassNeonSeekBar(
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
                Spacer(Modifier.width(16.dp))
                Text(formatHudTime(duration), color = TextMuted, fontSize = CineType.Body, fontFamily = PlexMonoFamily)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ExternalPlayerCommands.hasEpisodes) {
                    GlassIconButton(Icons.Rounded.SkipPrevious, "Previous") {
                        ExternalPlayerCommands.touched(); ExternalPlayerCommands.previous?.invoke()
                    }
                }
                GlassIconButton(Icons.Rounded.Replay10, "Back 10 seconds") {
                    ExternalPlayerCommands.touched()
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                }
                FrostedPlayButton(isPlaying = playing, isEnded = false, size = 84.dp, progress = shownFraction) {
                    ExternalPlayerCommands.touched()
                    if (player.isPlaying) player.pause() else player.play()
                }
                GlassIconButton(Icons.Rounded.Forward10, "Forward 10 seconds") {
                    ExternalPlayerCommands.touched()
                    val end = if (duration > 0L) duration else Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end))
                }
                if (ExternalPlayerCommands.hasEpisodes) {
                    GlassIconButton(Icons.Rounded.SkipNext, "Next") {
                        ExternalPlayerCommands.touched(); ExternalPlayerCommands.next?.invoke()
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPill(
                    text = if (ExternalPlayerCommands.subtitlesOn) "Subtitles on" else "Subtitles off",
                    icon = if (ExternalPlayerCommands.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                    selected = ExternalPlayerCommands.subtitlesOn,
                ) {
                    ExternalPlayerCommands.touched()
                    ExternalPlayerCommands.setSubtitles?.invoke(!ExternalPlayerCommands.subtitlesOn)
                }
                GlassPill("Audio", icon = Icons.Rounded.Audiotrack, selected = panel == Panel.AUDIO) {
                    ExternalPlayerCommands.touched()
                    panel = if (panel == Panel.AUDIO) null else Panel.AUDIO
                }
                GlassPill(speedLabel(speed), icon = Icons.Rounded.Speed, selected = panel == Panel.SPEED) {
                    ExternalPlayerCommands.touched()
                    panel = if (panel == Panel.SPEED) null else Panel.SPEED
                }
                if (ExternalPlayerHudState.fullFrame3d) {
                    GlassPill("3D full size", icon = Icons.Rounded.AspectRatio, selected = true) {
                        ExternalPlayerCommands.touched()
                    }
                } else {
                    GlassSizeCapsule(
                        scale = GlassesScreenSize.scale,
                        onSmaller = { ExternalPlayerCommands.touched(); GlassesScreenSize.smaller(sizeContext) },
                        onReset = { ExternalPlayerCommands.touched(); GlassesScreenSize.set(sizeContext, 1f) },
                        onBigger = { ExternalPlayerCommands.touched(); GlassesScreenSize.bigger(sizeContext) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionList(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .glassSurface(RoundedCornerShape(26.dp))
            .padding(18.dp)
            .widthIn(min = 280.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = TextMuted, fontSize = CineType.Label, fontWeight = FontWeight.Bold)
        options.forEachIndexed { i, (label, selected) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .glassSurface(CircleShape, lit = selected)
                    .clickable { onPick(i) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(label, color = if (selected) AmberCore else TextBright, fontSize = CineType.Body, fontWeight = FontWeight.Bold)
            }
        }
    }
}
