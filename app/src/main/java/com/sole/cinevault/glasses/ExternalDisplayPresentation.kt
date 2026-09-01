package com.sole.cinevault.glasses

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.R
import kotlin.math.roundToInt

data class ExternalDisplayInfo(
    val isConnected: Boolean,
    val displayId: Int?,
    val displayName: String?
)

/**
 * Live bridge between the dark host display and the Presentation shown in
 * video glasses. Coordinates are relative, like a laptop touchpad, so the
 * host and external display do not need matching size or aspect ratio.
 */
class ExternalPresentationHandle internal constructor(
    val playerView: PlayerView,
    val controlsVisible: MutableState<Boolean>,
    private val presentation: CineVaultVideoPresentation
) {
    fun showControls() = presentation.showControls()
    fun hideControls() = presentation.hideControls()
    fun movePointer(deltaX: Float, deltaY: Float) = presentation.movePointer(deltaX, deltaY)
    fun clickPointer(): Boolean = presentation.clickPointer()
    fun beginPointerDrag() = presentation.beginPointerDrag()
    fun endPointerDrag() = presentation.endPointerDrag()
    fun toggleControls() = presentation.toggleControls()
    fun openQuickSubtitles() = presentation.openQuickSubtitles()
    fun openGestureGuide() = presentation.openGestureGuide()
    fun updateSeekPreview(bitmap: Bitmap?, positionMs: Long, visible: Boolean) =
        presentation.updateSeekPreview(bitmap, positionMs, visible)
    fun applyViewportTransform(zoom: Float, panX: Float, panY: Float) =
        presentation.applyViewportTransform(zoom, panX, panY)
    fun updateResizeMode(resizeMode: Int) { playerView.resizeMode = resizeMode }
    fun showGestureHud(label: String, value: String? = null, progress: Int? = null) =
        presentation.showGestureHud(label, value, progress)
    fun showTouchPulse() = presentation.showTouchPulse()
    fun dismissForTabletReturn() = presentation.dismissForTabletReturn()
    fun enterTabletStandby() = presentation.enterTabletStandby()
}

@Composable
fun rememberExternalDisplayState(): State<ExternalDisplayInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(currentExternalDisplay(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { state.value = currentExternalDisplay(context) }
            override fun onDisplayRemoved(displayId: Int) { state.value = currentExternalDisplay(context) }
            override fun onDisplayChanged(displayId: Int) { state.value = currentExternalDisplay(context) }
        }
        manager.registerDisplayListener(listener, null)
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return state
}

@Composable
fun rememberExternalVideoPresentation(
    player: Player,
    externalDisplay: ExternalDisplayInfo,
    title: String,
    ratingText: String?,
    onBack: () -> Unit
): State<ExternalPresentationHandle?> {
    val context = LocalContext.current
    val handle = remember { mutableStateOf<ExternalPresentationHandle?>(null) }

    DisposableEffect(context, player, externalDisplay.displayId, title, ratingText) {
        val display = externalDisplay.displayId?.let { findDisplay(context, it) }
        if (display == null) {
            handle.value = null
            onDispose { }
        } else {
            val presentation = CineVaultVideoPresentation(
                outerContext = context,
                display = display,
                player = player,
                title = title,
                ratingText = ratingText,
                onBack = onBack,
                onReady = { handle.value = it },
                onDismissed = { handle.value = null }
            )
            try {
                presentation.show()
            } catch (_: WindowManager.InvalidDisplayException) {
                handle.value = null
            } catch (_: IllegalStateException) {
                handle.value = null
            }
            onDispose {
                presentation.detachPlayer()
                handle.value = null
                try { presentation.dismiss() } catch (_: Exception) { }
            }
        }
    }
    return handle
}

private fun currentExternalDisplay(context: Context): ExternalDisplayInfo {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
    return ExternalDisplayInfo(display != null, display?.displayId, display?.name)
}

private fun findDisplay(context: Context, displayId: Int): Display? {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.displayId == displayId && it.isValid }
}

@UnstableApi
internal class CineVaultVideoPresentation(
    outerContext: Context,
    display: Display,
    private val player: Player,
    private val title: String,
    private val ratingText: String?,
    private val onBack: () -> Unit,
    private val onReady: (ExternalPresentationHandle) -> Unit,
    private val onDismissed: () -> Unit
) : Presentation(outerContext, display) {
    private val handler = Handler(Looper.getMainLooper())
    // CineVault 2 "Space Glass" palette. Kept local because a Presentation
    // uses Android Views while the normal player uses Compose color tokens.
    private val spaceBlack = Color.rgb(5, 6, 10)
    private val spaceMid = Color.rgb(16, 19, 28)
    private val glassStrong = Color.argb(232, 20, 24, 34)
    private val glassSoft = Color.argb(180, 20, 24, 34)
    private val amber = Color.rgb(255, 194, 77)
    private val amberDeep = Color.rgb(176, 120, 24)
    private val textBright = Color.rgb(245, 243, 238)
    private val textMuted = Color.rgb(168, 166, 160)
    private val visibleState = mutableStateOf(false)
    private var playerView: PlayerView? = null
    private var root: FrameLayout? = null
    private var controls: View? = null
    private var pointer: View? = null
    private var seekBar: SeekBar? = null
    private var timeLabel: TextView? = null
    private var timePill: View? = null
    private var speedActionButton: LinearLayout? = null
    private var previewCard: LinearLayout? = null
    private var previewImage: ImageView? = null
    private var previewTime: TextView? = null
    private var quickSubtitlePanel: LinearLayout? = null
    private var speedPanel: LinearLayout? = null
    private var gestureGuidePanel: LinearLayout? = null
    private var gestureHud: LinearLayout? = null
    private var gestureHudLabel: TextView? = null
    private var gestureHudValue: TextView? = null
    private var gestureHudBar: View? = null
    private var playButton: ImageView? = null
    private var brandMark: View? = null
    private var pointerX = 0f
    private var pointerY = 0f
    private var draggingSeek = false
    private var subtitlesDisabled = false
    private var sleepMinutes = 0
    private var sleepRunnable: Runnable? = null
    private var controlsPinned = false
    private var subtitleTextSizeSp = 26f
    private var viewportScale = 1f
    private var viewportOffsetX = 0f
    private var viewportOffsetY = 0f

    private val hideRunnable = Runnable { hideControls() }
    private val hideHudRunnable = Runnable { gestureHud?.visibility = View.GONE }
    private val hideGuideRunnable = Runnable {
        gestureGuidePanel?.visibility = View.GONE
        if (visibleState.value) showControls()
    }
    private val glowRunnable = object : Runnable {
        private var bright = false
        override fun run() {
            bright = !bright
            brandMark?.animate()?.alpha(if (bright) 1f else 0.62f)?.setDuration(1_100L)?.start()
            handler.postDelayed(this, 1_150L)
        }
    }
    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (!draggingSeek) {
                val duration = p.duration.coerceAtLeast(0L)
                seekBar?.max = (duration / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seekBar?.progress = (p.currentPosition / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                timeLabel?.text = "${formatTime(p.currentPosition)}  /  ${formatTime(duration)}"
            }
            playButton?.setImageResource(if (p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            updateSpeedActionLabel(p.playbackParameters.speed)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val container = FrameLayout(context).apply { setBackgroundColor(spaceBlack) }
        // FIX: the real app's every screen has SpaceGlassBackground() —
        // two soft, blurred radial amber glows, top-left and bottom-
        // right, described in its own comment as "like light spilling
        // from a projector." Glasses Mode's background was flat
        // spaceBlack with no ambience at all — likely the single reason
        // this reads as "CineVault's colors" rather than "genuinely a
        // CineVault screen." Added before the video layer so it shows
        // through letterboxed edges and the standby blackout state,
        // while being naturally covered by actual video content when
        // it fills the screen — the same relationship the real
        // background effect has with foreground content elsewhere in
        // the app.
        container.addView(buildAmbientGlow())
        // A TextureView is deliberately used on the secondary display.
        // Several USB-C display stacks accepted subtitle/canvas output from
        // PlayerView while leaving its default SurfaceView black — confirmed
        // on RayNeo hardware, and TextureView is used unconditionally (not
        // gated to a specific vendor) since the fix generalizes to any
        // similar composition quirk. Worth re-confirming on another brand
        // once one's available, but nothing here assumes RayNeo specifically.
        val video = (LayoutInflater.from(context)
            .inflate(R.layout.external_player_view, container, false) as PlayerView).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            // The host screen transfers ownership atomically with
            // PlayerView.switchTargetView; attaching here can clear the
            // secondary-display video surface during the handoff.
            player = null
        }
        container.addView(video)
        container.addView(buildControlDock())
        container.addView(buildTimePill())
        container.addView(buildSeekPreview())
        container.addView(buildQuickSubtitlePanel())
        container.addView(buildSpeedPanel())
        container.addView(buildGestureGuidePanel())
        container.addView(buildGestureHud())
        container.addView(buildPointer())
        setContentView(container)
        root = container
        playerView = video
        container.post { alignTimePillToDock() }
        handler.post(progressRunnable)
        handler.post(glowRunnable)
        onReady(ExternalPresentationHandle(video, visibleState, this))
    }

    private fun buildControlDock(): View {
        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(15), dp(22), dp(15))
            background = glassBackground(glassStrong, dp(24).toFloat())
            visibility = View.GONE
            elevation = dp(14).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(690), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(42)
            }
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        header.addView(buildBrandMark())
        header.addView(iconButton(R.drawable.ic_arrow_back, "Back") {
            hideControls()
            onBack()
        })
        header.addView(TextView(context).apply {
            text = title
            setTextColor(textBright); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(8); marginEnd = dp(8) }
        })
        ratingText?.takeIf { it.isNotBlank() }?.let { scores ->
            header.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                scores.split("  •  ").forEach { score -> addView(ratingPill(score)) }
            })
        }
        dock.addView(header)
        dock.addView(divider())
        val transport = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(2))
        }
        transport.addView(symbolButton("↶10", "Seek back 10 seconds") { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); scheduleHide() })
        playButton = iconButton(R.drawable.ic_pause, "Play or pause", primary = true) {
            if (player.isPlaying) player.pause() else player.play()
            scheduleHide()
        }.also { transport.addView(it) }
        transport.addView(symbolButton("10↷", "Seek forward 10 seconds") { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))); scheduleHide() })
        dock.addView(transport)
        seekBar = SeekBar(context).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(amber)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(90, 168, 166, 160))
            thumbTintList = android.content.res.ColorStateList.valueOf(amber)
            layoutParams = LinearLayout.LayoutParams(-1, dp(30))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) { draggingSeek = true; handler.removeCallbacks(hideRunnable) }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) timeLabel?.text = "${formatTime(progress * 1000L)}   /   ${formatTime(player.duration)}" }
                override fun onStopTrackingTouch(seekBar: SeekBar) { player.seekTo(seekBar.progress * 1000L); draggingSeek = false; scheduleHide() }
            })
        }.also { dock.addView(it) }
        dock.addView(divider())
        val quickActions = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        quickActions.addView(textButton("CC", "Sub Studio") { openQuickSubtitles() })
        speedActionButton = textButton(formatSpeed(player.playbackParameters.speed), "Speed") { openSpeedPanel() }
            .also { quickActions.addView(it) }
        quickActions.addView(textButton("✦", "Gestures") { openGestureGuide() })
        dock.addView(quickActions)
        controls = dock
        return dock
    }

    private fun buildTimePill(): View = TextView(context).apply {
            setTextColor(textBright); textSize = 12f; gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            background = roundedBackground(Color.argb(165, 20, 24, 34), amberDeep, dp(16).toFloat())
            setPadding(dp(16), dp(5), dp(16), dp(5))
            visibility = View.GONE
            elevation = dp(30).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(190), dp(34), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = dp(330)
            }
            timeLabel = this
            timePill = this
    }

    private fun buildPointer(): View = View(context).apply {
        background = roundedBackground(Color.argb(95, 255, 194, 77), amber, dp(12).toFloat())
        visibility = View.GONE
        elevation = dp(100).toFloat()
        layoutParams = FrameLayout.LayoutParams(dp(28), dp(28))
        pointer = this
    }

    private fun buildGestureHud(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(14), dp(22), dp(14))
        background = glassBackground(Color.argb(238, 16, 19, 28), dp(22).toFloat())
        visibility = View.GONE
        elevation = dp(70).toFloat()
        layoutParams = FrameLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(42) }
        addView(TextView(context).apply {
            setTextColor(amber); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; letterSpacing = 0.12f
            gestureHudLabel = this
        })
        addView(TextView(context).apply {
            setTextColor(textBright); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(7))
            gestureHudValue = this
        })
        addView(FrameLayout(context).apply {
            background = roundedBackground(Color.argb(70, 255, 255, 255), Color.TRANSPARENT, dp(3).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, dp(6))
            addView(View(context).apply {
                background = roundedBackground(amber, amber, dp(3).toFloat())
                layoutParams = FrameLayout.LayoutParams(0, -1)
                gestureHudBar = this
            })
        })
        gestureHud = this
    }

    private fun buildSeekPreview(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = glassBackground(glassStrong, dp(16).toFloat())
        visibility = View.GONE
        layoutParams = FrameLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(224), dp(126))
            previewImage = this
        })
        addView(TextView(context).apply {
            setTextColor(amber); textSize = 14f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, 0)
            previewTime = this
        })
        previewCard = this
    }

    private fun buildQuickSubtitlePanel(): View = LinearLayout(context).apply quickPanel@ {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = glassBackground(glassStrong, dp(22).toFloat())
        visibility = View.GONE
        layoutParams = FrameLayout.LayoutParams(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "SUBTITLE STUDIO"; setTextColor(amber); textSize = 16f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            })
            addView(textButton("ON / OFF") {
                subtitlesDisabled = !subtitlesDisabled
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitlesDisabled).build()
            })
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(textButton("TEXT -") {
                subtitleTextSizeSp = (subtitleTextSizeSp - 2f).coerceAtLeast(14f)
                playerView?.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
            })
            addView(textButton("TEXT +") {
                subtitleTextSizeSp = (subtitleTextSizeSp + 2f).coerceAtMost(54f)
                playerView?.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
            })
            addView(textButton("SUB ↑") {
                val view = playerView?.subtitleView ?: return@textButton
                view.setBottomPaddingFraction(0.18f)
            })
            addView(textButton("SUB ↓") {
                val view = playerView?.subtitleView ?: return@textButton
                view.setBottomPaddingFraction(0.04f)
            })
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(textButton("RESET") {
                subtitleTextSizeSp = 26f
                playerView?.subtitleView?.apply {
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
                    setBottomPaddingFraction(0.08f)
                }
            })
            addView(textButton("×", "Close") { this@quickPanel.visibility = View.GONE; hideControls() })
            addView(textButton("↩", "Back") { this@quickPanel.visibility = View.GONE; showControls() })
        })
        quickSubtitlePanel = this
    }

    private fun buildSpeedPanel(): View = LinearLayout(context).apply panel@ {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = glassBackground(glassStrong, dp(22).toFloat())
        visibility = View.GONE
        layoutParams = FrameLayout.LayoutParams(dp(610), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(TextView(context).apply {
            text = "PLAYBACK SPEED"; setTextColor(amber); textSize = 16f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                addView(textButton(if (speed == 1f) "RESET" else "${speed}×") {
                    player.setPlaybackSpeed(speed); updateSpeedActionLabel(speed)
                    this@panel.visibility = View.GONE; showControls()
                })
            }
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            listOf(2.5f, 3f, 4f, 5f).forEach { speed ->
                addView(textButton("${speed}×") {
                    player.setPlaybackSpeed(speed); updateSpeedActionLabel(speed)
                    this@panel.visibility = View.GONE; showControls()
                })
            }
        })
        addView(textButton("↩", "Back") { this@panel.visibility = View.GONE; showControls() })
        speedPanel = this
    }

    private fun buildGestureGuidePanel(): View = LinearLayout(context).apply guide@ {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(20), dp(24), dp(20))
        background = glassBackground(Color.argb(245, 20, 24, 34), dp(24).toFloat())
        visibility = View.GONE
        layoutParams = FrameLayout.LayoutParams(dp(590), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(TextView(context).apply {
            text = "RAYNEO TOUCHPAD GESTURES"
            setTextColor(amber); textSize = 17f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        addView(TextView(context).apply {
            text = listOf(
                "Single tap  •  Show controls / select highlighted item",
                "Double tap  •  Play / Pause",
                "Centre horizontal drag  •  Precision seek",
                "Left vertical drag  •  Tablet brightness (with HUD)",
                "Right vertical drag  •  Media volume",
                "Two-finger pinch / pan  •  Video scale and position",
                "Long-press  •  Quick Subtitles",
                "Swipe inward from left / right edge  •  Previous / next episode",
                "Five-finger spread  •  Emergency return to tablet"
            ).joinToString("\n")
            setTextColor(textBright); textSize = 14f
            setLineSpacing(0f, 1.18f)
        })
        addView(TextView(context).apply {
            text = "The five-finger escape ends only this Glasses Mode session. Playback continues on the tablet."
            setTextColor(textMuted); textSize = 12f; gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        })
        addView(textButton("GOT IT") {
            this@guide.visibility = View.GONE
            showControls()
        })
        gestureGuidePanel = this
    }

    // FIX: replaces raw Unicode symbol glyphs / the old-style Android
    // system drawable (android.R.drawable.ic_menu_revert) with real
    // vector icons matching the app's actual icon language — every
    // real screen uses Material Icons' filled, rounded style
    // (Icons.Filled.* in Compose). A Presentation is View-based, so
    // those Compose ImageVector objects can't be referenced directly;
    // this uses hand-authored vector drawables with the same standard
    // Material path data instead, tinted the same way symbolButton's
    // text was. Used only for icons simple and standard enough to be
    // confident the path data is exactly right (back, play, pause) —
    // the seek ±10s buttons keep their existing text form rather than
    // risk a hand-typed path for a more complex icon shape being subtly
    // wrong.
    private fun iconButton(iconRes: Int, description: String, primary: Boolean = false, action: () -> Unit) = ImageView(context).apply {
        setImageResource(iconRes)
        setColorFilter(if (primary) amber else textBright)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = if (primary) primaryButtonBackground() else roundedBackground(Color.argb(75, 255, 194, 77), amberDeep, dp(20).toFloat())
        contentDescription = description
        val glowExtent = if (primary) 10 else 0
        val iconPadding = dp(if (primary) 20 + glowExtent else 15)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        layoutParams = LinearLayout.LayoutParams(
            if (primary) dp(68 + glowExtent * 2) else dp(62),
            if (primary) dp(68 + glowExtent * 2) else dp(56)
        ).apply { marginStart = dp(if (primary) 9 - glowExtent else 9); marginEnd = dp(if (primary) 9 - glowExtent else 9) }
        setOnClickListener { action() }
    }

    private fun symbolButton(symbol: String, description: String, primary: Boolean = false, action: () -> Unit) = TextView(context).apply {
        text = symbol
        setTextColor(if (primary) amber else textBright)
        textSize = if (primary) 48f else 24f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        // FIX: the primary (play/pause) button previously had no glow at
        // all — just an amber-tinted pill. The real design system calls
        // its amberGlow() modifier "the signature glow" and uses it
        // specifically behind active/primary elements like this one.
        // primaryButtonBackground() reproduces it: a soft radial amber
        // gradient extending beyond the button's own bounds, with the
        // actual pill shape layered on top.
        background = if (primary) primaryButtonBackground() else roundedBackground(Color.argb(75, 255, 194, 77), amberDeep, dp(20).toFloat())
        contentDescription = description
        // Primary button's overall bounds are larger than its visual pill
        // size specifically to leave room for the glow around it — see
        // primaryButtonBackground(). The pill itself still renders at
        // the original 68dp; glowExtent below must match what's inset
        // there.
        val glowExtent = 10
        layoutParams = LinearLayout.LayoutParams(
            if (primary) dp(68 + glowExtent * 2) else dp(62),
            if (primary) dp(68 + glowExtent * 2) else dp(56)
        ).apply { marginStart = dp(if (primary) 9 - glowExtent else 9); marginEnd = dp(if (primary) 9 - glowExtent else 9) }
        setOnClickListener { action() }
    }

    // Glow extends into the full (larger) button bounds; the pill is
    // inset inward by the same amount so it keeps its original 68dp
    // visual size, centered within the extra space the glow occupies.
    private fun primaryButtonBackground(): Drawable {
        val glowExtent = dp(10)
        val glow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(Color.argb(90, 232, 160, 32), Color.argb(30, 110, 74, 16), Color.TRANSPARENT)
            gradientRadius = dp(56).toFloat()
        }
        val pill = roundedBackground(Color.argb(82, 255, 194, 77), amber, dp(34).toFloat())
        return LayerDrawable(arrayOf(glow, pill)).apply {
            setLayerInset(1, glowExtent, glowExtent, glowExtent, glowExtent)
        }
    }

    // Two large, soft radial-gradient circles positioned top-left and
    // bottom-right — same technique as the primary button's glow, just
    // sized for the whole screen and positioned via layout gravity
    // instead of LayerDrawable insets. dp() below resolves against
    // THIS Presentation's own context (the external display's own
    // density), not the tablet's, so this already scales correctly
    // across displays with different pixel density. What's still
    // untested across brands is proportion, not density: these are
    // fixed dp sizes tuned by eye against RayNeo's ~960dp-wide logical
    // display, so a glasses model reporting a notably different
    // logical width/aspect ratio may render this glow too large, too
    // small, or off-position relative to the screen edges. Purely
    // cosmetic (doesn't affect touch/controls/subtitles), worth a
    // once-over once another brand is actually in hand.
    private fun buildAmbientGlow(): View = FrameLayout(context).apply {
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(Color.argb(40, 232, 160, 32), Color.TRANSPARENT)
                gradientRadius = dp(260).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(dp(520), dp(520), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(-160); topMargin = dp(-140)
            }
        })
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(Color.argb(32, 110, 74, 16), Color.TRANSPARENT)
                gradientRadius = dp(220).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(dp(440), dp(440), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(-130); bottomMargin = dp(-120)
            }
        })
        layoutParams = FrameLayout.LayoutParams(-1, -1)
    }

    private fun buildBrandMark(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundedBackground(Color.argb(75, 255, 194, 77), amber, dp(18).toFloat())
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = LinearLayout.LayoutParams(dp(138), dp(54)).apply { marginEnd = dp(8) }
        addView(TextView(context).apply {
            text = "CINEVAULT"; setTextColor(amber); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; letterSpacing = 0.13f
            setShadowLayer(dp(8).toFloat(), 0f, 0f, amber)
        })
        addView(TextView(context).apply {
            text = "GLASSES MODE"; setTextColor(textBright); textSize = 8f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; letterSpacing = 0.12f
        })
        brandMark = this
    }

    private fun textButton(icon: String, label: String = icon, action: () -> Unit) = LinearLayout(context).apply button@ {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = glassBackground(glassSoft, dp(18).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(126), dp(44)).apply { marginStart = dp(5); marginEnd = dp(5) }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(TextView(context).apply {
            text = icon; setTextColor(amber); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        if (label != icon) addView(TextView(context).apply {
            text = label.uppercase(); setTextColor(textBright); textSize = 9f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f; gravity = Gravity.CENTER
            setPadding(dp(7), 0, 0, 0)
        })
    }

    private fun ratingPill(score: String) = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
        background = roundedBackground(Color.argb(75, 255, 194, 77), amberDeep, dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginStart = dp(5) }
        val (iconRes, value) = when {
            score.startsWith("IMDb ") -> R.drawable.ic_imdb to score.removePrefix("IMDb ")
            score.startsWith("TMDB ") -> R.drawable.ic_tmdb to score.removePrefix("TMDB ")
            score.startsWith("RT ") -> R.drawable.ic_rotten_tomatoes to score.removePrefix("RT ")
            else -> 0 to score
        }
        if (iconRes != 0) addView(ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(20)).apply { marginEnd = dp(5) }
        })
        addView(TextView(context).apply {
            text = value; setTextColor(textBright); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(Color.argb(38, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(7); bottomMargin = dp(7) }
    }

    fun showControls() {
        val r = root ?: return
        controls?.visibility = View.VISIBLE
        timePill?.visibility = View.VISIBLE
        pointer?.visibility = View.VISIBLE
        visibleState.value = true
        if (pointerX == 0f && pointerY == 0f) {
            pointerX = r.width * 0.5f; pointerY = r.height * 0.68f
            updatePointerPosition()
        }
        scheduleHide()
    }

    fun hideControls() {
        if (controlsPinned) return
        controls?.visibility = View.GONE
        timePill?.visibility = View.GONE
        if (quickSubtitlePanel?.visibility != View.VISIBLE &&
            gestureGuidePanel?.visibility != View.VISIBLE &&
            speedPanel?.visibility != View.VISIBLE) pointer?.visibility = View.GONE
        visibleState.value = false
        handler.removeCallbacks(hideRunnable)
    }

    fun toggleControls() {
        if (visibleState.value) {
            quickSubtitlePanel?.visibility = View.GONE
            val pinned = controlsPinned
            controlsPinned = false
            hideControls()
            controlsPinned = pinned
        } else showControls()
    }

    fun openQuickSubtitles() {
        gestureGuidePanel?.visibility = View.GONE
        speedPanel?.visibility = View.GONE
        showControls()
        controls?.visibility = View.GONE
        timePill?.visibility = View.GONE
        quickSubtitlePanel?.visibility = View.VISIBLE
        pointer?.visibility = View.VISIBLE
        handler.removeCallbacks(hideRunnable)
    }

    fun openGestureGuide() {
        quickSubtitlePanel?.visibility = View.GONE
        speedPanel?.visibility = View.GONE
        showControls()
        controls?.visibility = View.GONE
        timePill?.visibility = View.GONE
        pointer?.visibility = View.VISIBLE
        visibleState.value = true
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(hideGuideRunnable)
        gestureGuidePanel?.visibility = View.VISIBLE
        root?.bringChildToFront(pointer)
        handler.postDelayed(hideGuideRunnable, 15_000L)
    }

    private fun openSpeedPanel() {
        quickSubtitlePanel?.visibility = View.GONE
        gestureGuidePanel?.visibility = View.GONE
        showControls()
        controls?.visibility = View.GONE
        timePill?.visibility = View.GONE
        speedPanel?.visibility = View.VISIBLE
        pointer?.visibility = View.VISIBLE
        handler.removeCallbacks(hideRunnable)
        root?.bringChildToFront(pointer)
    }

    fun showGestureHud(label: String, value: String?, progress: Int?) {
        gestureHudLabel?.text = label.uppercase()
        gestureHudValue?.text = value.orEmpty()
        val bar = gestureHudBar ?: return
        val parent = bar.parent as? View ?: return
        parent.post {
            val fraction = (progress ?: 0).coerceIn(0, 100) / 100f
            bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).apply {
                width = (parent.width * fraction).roundToInt()
            }
            bar.requestLayout()
        }
        gestureHud?.visibility = View.VISIBLE
        handler.removeCallbacks(hideHudRunnable)
        handler.postDelayed(hideHudRunnable, 1_200L)
    }

    fun showTouchPulse() {
        pointer?.animate()?.cancel()
        pointer?.scaleX = 1f
        pointer?.scaleY = 1f
        pointer?.alpha = 1f
        pointer?.animate()?.scaleX(1.7f)?.scaleY(1.7f)?.alpha(0.45f)?.setDuration(120L)?.withEndAction {
            pointer?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(1f)?.setDuration(140L)?.start()
        }?.start()
    }

    fun updateSeekPreview(bitmap: Bitmap?, positionMs: Long, visible: Boolean) {
        previewCard?.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            previewImage?.setImageBitmap(bitmap)
            previewTime?.text = formatTime(positionMs)
        }
    }

    fun applyViewportTransform(zoom: Float, panX: Float, panY: Float) {
        val video = playerView ?: return
        viewportScale = (viewportScale * zoom).coerceIn(1f, 3f)
        val maxX = video.width * (viewportScale - 1f) / 2f
        val maxY = video.height * (viewportScale - 1f) / 2f
        viewportOffsetX = (viewportOffsetX + panX).coerceIn(-maxX, maxX)
        viewportOffsetY = (viewportOffsetY + panY).coerceIn(-maxY, maxY)
        if (viewportScale <= 1.02f) {
            viewportScale = 1f; viewportOffsetX = 0f; viewportOffsetY = 0f
        }
        video.scaleX = viewportScale
        video.scaleY = viewportScale
        video.translationX = viewportOffsetX
        video.translationY = viewportOffsetY
    }

    fun movePointer(deltaX: Float, deltaY: Float) {
        val r = root ?: return
        if (controls?.visibility != View.VISIBLE &&
            quickSubtitlePanel?.visibility != View.VISIBLE &&
            gestureGuidePanel?.visibility != View.VISIBLE &&
            speedPanel?.visibility != View.VISIBLE) showControls()
        pointerX = (pointerX + deltaX * 1.45f).coerceIn(0f, (r.width - dp(24)).toFloat())
        pointerY = (pointerY + deltaY * 1.45f).coerceIn(0f, (r.height - dp(24)).toFloat())
        updatePointerPosition()
        if (draggingSeek) {
            val now = android.os.SystemClock.uptimeMillis()
            r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, pointerX + dp(12), pointerY + dp(12), 0))
        }
    }

    private fun updatePointerPosition() {
        pointer?.x = pointerX
        pointer?.y = pointerY
        root?.bringChildToFront(pointer)
    }

    private fun alignTimePillToDock() {
        val dock = controls ?: return
        val pill = timePill ?: return
        pill.x = (dock.right - pill.width).toFloat()
    }

    private fun updateSpeedActionLabel(speed: Float) {
        val icon = speedActionButton?.getChildAt(0) as? TextView ?: return
        icon.text = formatSpeed(speed)
    }

    private fun formatSpeed(speed: Float): String {
        val value = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')
        return "${value}×"
    }

    fun clickPointer(): Boolean {
        val r = root ?: return false
        val x = pointerX + dp(12)
        val y = pointerY + dp(12)
        if (findClickableAt(r, x, y) == null) {
            return false
        }
        val now = android.os.SystemClock.uptimeMillis()
        r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        r.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0))
        if (controls?.visibility == View.VISIBLE) scheduleHide()
        return true
    }

    private fun findClickableAt(parent: ViewGroup, x: Float, y: Float): View? {
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            if (child.visibility != View.VISIBLE || x < child.left || x > child.right || y < child.top || y > child.bottom) continue
            if (child is ViewGroup) {
                findClickableAt(child, x - child.left, y - child.top)?.let { return it }
            }
            if (child.isClickable || child is SeekBar) return child
        }
        return null
    }

    fun beginPointerDrag() {
        val r = root ?: return
        val x = pointerX + dp(12)
        val y = pointerY + dp(12)
        // Only start a synthetic drag when the pointer is actually over the
        // seek bar. Ordinary touchpad movement must never press buttons.
        val seek = seekBar ?: return
        val location = IntArray(2)
        seek.getLocationOnScreen(location)
        val rootLocation = IntArray(2)
        r.getLocationOnScreen(rootLocation)
        val localLeft = location[0] - rootLocation[0]
        val localTop = location[1] - rootLocation[1]
        if (x !in localLeft.toFloat()..(localLeft + seek.width).toFloat() ||
            y !in localTop.toFloat()..(localTop + seek.height).toFloat()) return
        draggingSeek = true
        val now = android.os.SystemClock.uptimeMillis()
        r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        handler.removeCallbacks(hideRunnable)
    }

    fun endPointerDrag() {
        if (!draggingSeek) return
        val r = root ?: return
        val x = pointerX + dp(12)
        val y = pointerY + dp(12)
        val now = android.os.SystemClock.uptimeMillis()
        r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0))
        draggingSeek = false
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        if (!controlsPinned &&
            quickSubtitlePanel?.visibility != View.VISIBLE &&
            gestureGuidePanel?.visibility != View.VISIBLE &&
            speedPanel?.visibility != View.VISIBLE) {
            handler.postDelayed(hideRunnable, 6_000L)
        }
    }

    fun detachPlayer() {
        handler.removeCallbacksAndMessages(null)
        playerView?.player = null
        playerView = null
    }

    fun dismissForTabletReturn() {
        detachPlayer()
        try { dismiss() } catch (_: Exception) { }
    }

    fun enterTabletStandby() {
        playerView?.player = null
        controls?.visibility = View.GONE
        timePill?.visibility = View.GONE
        quickSubtitlePanel?.visibility = View.GONE
        speedPanel?.visibility = View.GONE
        gestureGuidePanel?.visibility = View.GONE
        gestureHud?.visibility = View.GONE
        pointer?.visibility = View.GONE
        visibleState.value = false
        root?.setBackgroundColor(Color.BLACK)
    }

    override fun onDisplayRemoved() { detachPlayer(); onDismissed(); super.onDisplayRemoved() }
    // Locking the tablet also stops the Presentation window. Detaching the
    // video surface here caused glasses video to turn black while audio kept
    // playing. Real cleanup is owned by onDisplayRemoved()/DisposableEffect.
    override fun onStop() { super.onStop() }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
    private fun roundedBackground(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = radius
    }
    // FIX: was a single GradientDrawable with a flat amber stroke — every
    // real glass panel in the app (glassPanel() in Glass.kt) uses a WHITE
    // gradient border instead — bright at the top where light would
    // naturally catch an edge, fading to almost nothing at the bottom —
    // which is what actually reads as "glass" rather than "a panel with
    // a colored outline." GradientDrawable.setStroke() only accepts one
    // flat color; it can't do a gradient stroke directly, so this layers
    // three drawables instead: an outer one gradient-filled with the
    // border colors (visible only at the edge, since the inner layers
    // sit on top inset by the border width), the actual panel fill in
    // the middle, and a semi-transparent white sheen on top that fades
    // out over the top ~half — the same two-layer "fill plus separate
    // highlight" split the real glassPanel() modifier uses, rather than
    // baking everything into one fixed gradient the way this used to.
    private fun glassBackground(fill: Int, radius: Float): Drawable {
        val borderWidth = dp(1)
        val innerRadius = (radius - borderWidth).coerceAtLeast(0f)
        val border = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(102, 255, 255, 255), Color.argb(18, 255, 255, 255))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }
        val fillLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = innerRadius
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(20, 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = innerRadius
        }
        return LayerDrawable(arrayOf(border, fillLayer, sheen)).apply {
            setLayerInset(1, borderWidth, borderWidth, borderWidth, borderWidth)
            setLayerInset(2, borderWidth, borderWidth, borderWidth, borderWidth)
        }
    }
    private fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val hours = total / 3600L; val minutes = (total % 3600L) / 60L; val seconds = total % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}
