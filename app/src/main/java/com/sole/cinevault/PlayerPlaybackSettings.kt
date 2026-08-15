package com.sole.cinevault

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/*
 * PlayerPlaybackSettings.kt
 *
 * Owns the small playback-setting behaviours that do not belong to the
 * rendering surface: speed selection and the sleep-timer countdown. The state
 * holder is remembered by VideoPlayerScreen, so its existing menus and
 * external-display UI continue to observe exactly the same values.
 */

@OptIn(UnstableApi::class)
internal class PlayerPlaybackSettingsState {
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var sleepTimerMinutes by mutableIntStateOf(0)
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
    var sleepTimerActive by mutableStateOf(false)

    fun applyPlaybackSpeed(
        context: Context,
        player: ExoPlayer,
        haptics: HapticFeedback,
        speed: Float,
        onDismissMenu: () -> Unit,
    ) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playbackSpeed = speed
        player.playbackParameters = PlaybackParameters(speed)
        onDismissMenu()
        Toast.makeText(context, "${speed}x speed", Toast.LENGTH_SHORT).show()
    }

    fun applySleepTimer(
        context: Context,
        haptics: HapticFeedback,
        minutes: Int,
        onDismissMenu: () -> Unit,
    ) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        sleepTimerMinutes = minutes
        if (minutes == 0) {
            sleepTimerActive = false
            sleepTimerRemainingMs = 0L
            Toast.makeText(context, "Sleep timer off", Toast.LENGTH_SHORT).show()
        } else {
            sleepTimerRemainingMs = minutes * 60 * 1000L
            sleepTimerActive = true
            Toast.makeText(
                context,
                "Sleep timer: ${minutes}min",
                Toast.LENGTH_SHORT,
            ).show()
        }
        onDismissMenu()
    }
}

@Composable
@OptIn(UnstableApi::class)
internal fun PlayerSleepTimerEffect(
    context: Context,
    player: ExoPlayer,
    settings: PlayerPlaybackSettingsState,
) {
    LaunchedEffect(settings.sleepTimerActive, settings.sleepTimerRemainingMs) {
        if (settings.sleepTimerActive && settings.sleepTimerRemainingMs > 0L) {
            delay(1000)
            settings.sleepTimerRemainingMs -= 1000L
            if (settings.sleepTimerRemainingMs <= 0L) {
                settings.sleepTimerActive = false
                settings.sleepTimerRemainingMs = 0L
                player.pause()
                Toast.makeText(
                    context,
                    "Sleep timer — playback paused",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
