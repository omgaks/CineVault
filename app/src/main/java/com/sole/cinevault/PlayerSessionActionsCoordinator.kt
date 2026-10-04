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
        CineVaultToast.show(context, "${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}x speed")
    }

    fun setSleepTimer(minutes: Int) {
        performSelectionHaptic()
        setSleepTimerMinutes(minutes)

        if (playerSleepTimerIsOff(minutes)) {
            setSleepTimerActive(false)
            setSleepTimerRemainingMs(0L)
            CineVaultToast.show(context, "Sleep timer off")
        } else {
            setSleepTimerRemainingMs(playerSleepTimerDurationMs(minutes))
            setSleepTimerActive(true)
            CineVaultToast.show(context, "Sleep timer: $minutes min")
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
