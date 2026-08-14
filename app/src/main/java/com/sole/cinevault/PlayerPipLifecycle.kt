package com.sole.cinevault

import android.app.PictureInPictureParams
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.exoplayer.ExoPlayer

internal const val CINEVAULT_PIP_ACTION = "com.sole.cinevault.PIP_ACTION"
internal const val CINEVAULT_PIP_ACTION_EXTRA = "pip_action"

/** Keeps the screen awake during playback and refreshes Android's PiP actions. */
@Composable
internal fun PlayerPipWindowEffect(
    activity: Activity?,
    context: Context,
    isPlaying: Boolean
) {
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setActions(buildPipActions(context, isPlaying))
                        .build()
                )
            } catch (_: Exception) {
                // PiP can be unavailable during device-specific window states.
            }
        }
    }
}

/** Registers the action receiver used by Android's system-drawn PiP controls. */
@Composable
internal fun PlayerPipActionReceiverEffect(
    context: Context,
    player: ExoPlayer
) {
    DisposableEffect(player) {
        CineVaultPlayerHolder.currentPlayer = player
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != CINEVAULT_PIP_ACTION) return
                val activePlayer = CineVaultPlayerHolder.currentPlayer ?: return
                when (intent.getIntExtra(CINEVAULT_PIP_ACTION_EXTRA, -1)) {
                    0 -> {
                        if (activePlayer.isPlaying) {
                            activePlayer.pause()
                        } else {
                            activePlayer.play()
                            activePlayer.playWhenReady = true
                        }
                    }

                    1 -> activePlayer.seekTo(
                        (activePlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                    )

                    2 -> activePlayer.seekTo(
                        (activePlayer.currentPosition + 10_000L)
                            .coerceAtMost(activePlayer.duration.coerceAtLeast(0L))
                    )
                }
            }
        }

        val filter = IntentFilter(CINEVAULT_PIP_ACTION)
        // The PendingIntents backing CineVault's PiP buttons are created by
        // this app, so no other package needs to reach this receiver.
        // ContextCompat applies the private/non-exported contract across
        // every supported API level, including pre-Android 13 devices where
        // the platform registerReceiver overload has no explicit flag.
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            try {
                context.applicationContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Receiver may already be removed during process teardown.
            }
        }
    }
}
