package com.sole.cinevault

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.audiofx.AudioFxController
import kotlinx.coroutines.delay
import com.sole.cinevault.library.savePlaybackPosition

@Composable
internal fun PlayerSessionLifecycle(
    context: Context,
    activity: Activity?,
    player: ExoPlayer,
    videoPath: String,
    isStreamMedia: Boolean,
    audioFxController: AudioFxController,
    onNextRequested: () -> Unit,
    onPreviousRequested: () -> Unit,
    onInitialBrightnessChanged: (Int) -> Unit,
) {
    val currentVideoPath by rememberUpdatedState(videoPath)
    val currentIsStreamMedia by rememberUpdatedState(isStreamMedia)
    val currentNextRequested by rememberUpdatedState(onNextRequested)
    val currentPreviousRequested by rememberUpdatedState(onPreviousRequested)

    LaunchedEffect(player) {
        CineVaultPlayerHolder.currentPlayer = player
        CineVaultPlayerHolder.onNextRequested = { currentNextRequested() }
        CineVaultPlayerHolder.onPreviousRequested = { currentPreviousRequested() }
        audioFxController.attachToPlayer(player)
        audioFxController.startAutoSwitching()

        ContextCompat.startForegroundService(
            context,
            Intent(context, CineVaultPlaybackService::class.java),
        )

        val brightness = try {
            val raw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            ((raw / 255f) * 100f).toInt().coerceIn(5, 100)
        } catch (_: Exception) {
            70
        }
        onInitialBrightnessChanged(brightness)
        activity?.enterImmersiveModeForPlayer()
    }

    LaunchedEffect(player, videoPath, audioFxController.loudnessNormalizationEnabled) {
        if (!audioFxController.loudnessNormalizationEnabled || isStreamMedia) return@LaunchedEffect
        while (player.playbackState != androidx.media3.common.Player.STATE_READY) {
            delay(250L)
        }
        val durationMs = player.duration
        if (durationMs > 0L && durationMs != androidx.media3.common.C.TIME_UNSET) {
            audioFxController.analyzeAndNormalizeLoudnessOnce(videoPath, durationMs)
        }
    }

    DisposableEffect(player) {
        onDispose {
            if (!currentIsStreamMedia) {
                savePlaybackPosition(
                    context,
                    currentVideoPath,
                    player.currentPosition.coerceAtLeast(0L),
                )
            }
            audioFxController.stopAutoSwitching()
            audioFxController.detachFromPlayer(player)
            audioFxController.release()
            player.release()
            AudioSyncHolder.offsetUs = 0L
            if (CineVaultPlayerHolder.currentPlayer == player) {
                CineVaultPlayerHolder.currentPlayer = null
            }
            CineVaultPlayerHolder.onNextRequested = null
            CineVaultPlayerHolder.onPreviousRequested = null
            context.stopService(Intent(context, CineVaultPlaybackService::class.java))
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (_: Exception) {
            }
            activity?.exitImmersiveModeForPlayer()
        }
    }
}
