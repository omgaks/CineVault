package com.sole.cinevault

import android.content.Context
import android.widget.Toast
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer

/**
 * Slice 21: owns the two small player-session actions that previously
 * mutated UI state and ExoPlayer directly inside VideoPlayerScreen:
 * playback speed and the sleep timer.
 *
 * No presentation logic lives here. The composable still decides when
 * menus are visible and when the timer effect runs; this coordinator only
 * performs the state/player mutations for those actions.
 */
class PlayerSessionActionsCoordinator(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val performSelectionHaptic: () -> Unit,
    private val setPlaybackSpeedState: (Float) -> Unit,
    private val setSleepTimerMinutes: (Int) -> Unit,
    private val getSleepTimerActive: () -> Boolean,
    private val setSleepTimerActive: (Boolean) -> Unit,
    private val getSleepTimerRemainingMs: () -> Long,
    private val setSleepTimerRemainingMs: (Long) -> Unit,
    private val closeSpeedMenu: () -> Unit,
    private val closeSleepMenu: () -> Unit,
    private val showControls: () -> Unit,
) {
    fun setPlaybackSpeed(speed: Float) {
        performSelectionHaptic()
        setPlaybackSpeedState(speed)
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        closeSpeedMenu()
        showControls()
        Toast.makeText(context, "${speed}x speed", Toast.LENGTH_SHORT).show()
    }

    fun setSleepTimer(minutes: Int) {
        performSelectionHaptic()
        setSleepTimerMinutes(minutes)

        if (playerSleepTimerIsOff(minutes)) {
            setSleepTimerActive(false)
            setSleepTimerRemainingMs(0L)
            Toast.makeText(context, "Sleep timer off", Toast.LENGTH_SHORT).show()
        } else {
            setSleepTimerRemainingMs(playerSleepTimerDurationMs(minutes))
            setSleepTimerActive(true)
            Toast.makeText(context, "Sleep timer: ${minutes}min", Toast.LENGTH_SHORT).show()
        }

        closeSleepMenu()
        showControls()
    }

    fun shouldTickSleepTimer(): Boolean =
        playerShouldTickSleepTimer(
            getSleepTimerActive(),
            getSleepTimerRemainingMs(),
        )

    fun tickSleepTimer() {
        val remaining = playerSleepTimerRemainingAfterTick(
            getSleepTimerRemainingMs()
        )
        setSleepTimerRemainingMs(remaining)

        if (playerSleepTimerHasExpired(remaining)) {
            setSleepTimerActive(false)
            setSleepTimerRemainingMs(0L)
            exoPlayer.pause()
            Toast.makeText(
                context,
                "Sleep timer — playback paused",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
