package com.sole.cinevault

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

/** A presentation-capable display other than the phone/tablet panel. */
data class ExternalDisplayInfo(
    val isConnected: Boolean,
    val displayId: Int?,
    val displayName: String?
)

/**
 * Observes USB-C DisplayPort, HDMI, wireless-display and RayNeo display
 * hot-plug events. Only displays Android exposes in the presentation category
 * are accepted; virtual helper displays and the device panel are ignored.
 */
@Composable
fun rememberExternalDisplayState(): State<ExternalDisplayInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(currentExternalDisplay(context)) }

    DisposableEffect(context) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                state.value = currentExternalDisplay(context)
            }

            override fun onDisplayRemoved(displayId: Int) {
                state.value = currentExternalDisplay(context)
            }

            override fun onDisplayChanged(displayId: Int) {
                state.value = currentExternalDisplay(context)
            }
        }
        displayManager.registerDisplayListener(listener, null)
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }
    return state
}

/**
 * Moves the player's video surface to a real Android Presentation on the
 * external display. The same ExoPlayer remains alive, so playback position,
 * buffering, MediaSession controls, audio selection and subtitles survive the
 * handoff. The returned PlayerView is used by Subtitle Studio so external
 * display styling is applied to the surface the viewer actually sees.
 *
 * On unplug, dismissal, recomposition, or navigation away, the Presentation
 * releases only its view/surface—not ExoPlayer. VideoPlayerScreen then attaches
 * the same player back to its local PlayerView.
 */
@Composable
fun rememberExternalVideoPresentation(
    player: Player,
    externalDisplay: ExternalDisplayInfo
): State<PlayerView?> {
    val context = LocalContext.current
    val externalPlayerView = remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(context, player, externalDisplay.displayId) {
        val display = externalDisplay.displayId?.let { findDisplay(context, it) }
        if (display == null) {
            externalPlayerView.value = null
            onDispose { }
        } else {
            val presentation = CineVaultVideoPresentation(
                outerContext = context,
                display = display,
                player = player,
                onPlayerViewReady = { externalPlayerView.value = it },
                onPresentationDismissed = { externalPlayerView.value = null }
            )

            try {
                presentation.show()
            } catch (_: WindowManager.InvalidDisplayException) {
                externalPlayerView.value = null
            } catch (_: IllegalStateException) {
                externalPlayerView.value = null
            }

            onDispose {
                presentation.detachPlayer()
                externalPlayerView.value = null
                try {
                    presentation.dismiss()
                } catch (_: Exception) {
                    // The display may already have disappeared.
                }
            }
        }
    }
    return externalPlayerView
}

private fun currentExternalDisplay(context: Context): ExternalDisplayInfo {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val external = displayManager
        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
    return ExternalDisplayInfo(
        isConnected = external != null,
        displayId = external?.displayId,
        displayName = external?.name
    )
}

private fun findDisplay(context: Context, displayId: Int): Display? {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    return displayManager
        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.displayId == displayId && it.isValid }
}

@OptIn(UnstableApi::class)
private class CineVaultVideoPresentation(
    outerContext: Context,
    display: Display,
    private val player: Player,
    private val onPlayerViewReady: (PlayerView) -> Unit,
    private val onPresentationDismissed: () -> Unit
) : Presentation(outerContext, display) {

    private var presentationPlayerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val playerView = PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val fallbackLabel = TextView(context).apply {
            text = "CineVault"
            setTextColor(android.graphics.Color.rgb(201, 167, 101))
            textSize = 13f
            gravity = Gravity.CENTER
            alpha = 0.7f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        root.addView(fallbackLabel)
        root.addView(playerView)
        setContentView(root)

        presentationPlayerView = playerView
        playerView.player = player
        onPlayerViewReady(playerView)
    }

    fun detachPlayer() {
        presentationPlayerView?.player = null
        presentationPlayerView = null
    }

    override fun onDisplayRemoved() {
        detachPlayer()
        onPresentationDismissed()
        super.onDisplayRemoved()
    }

    override fun onStop() {
        detachPlayer()
        onPresentationDismissed()
        super.onStop()
    }
}
