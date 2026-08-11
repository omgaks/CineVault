package com.sole.cinevault.glasses

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.FrameLayout
import android.widget.ImageButton
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
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
    fun clickPointer() = presentation.clickPointer()
    fun updateResizeMode(resizeMode: Int) { playerView.resizeMode = resizeMode }
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
    externalDisplay: ExternalDisplayInfo
): State<ExternalPresentationHandle?> {
    val context = LocalContext.current
    val handle = remember { mutableStateOf<ExternalPresentationHandle?>(null) }

    DisposableEffect(context, player, externalDisplay.displayId) {
        val display = externalDisplay.displayId?.let { findDisplay(context, it) }
        if (display == null) {
            handle.value = null
            onDispose { }
        } else {
            val presentation = CineVaultVideoPresentation(
                outerContext = context,
                display = display,
                player = player,
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
    private val onReady: (ExternalPresentationHandle) -> Unit,
    private val onDismissed: () -> Unit
) : Presentation(outerContext, display) {
    private val handler = Handler(Looper.getMainLooper())
    private val amber = Color.rgb(201, 167, 101)
    private val visibleState = mutableStateOf(false)
    private var playerView: PlayerView? = null
    private var root: FrameLayout? = null
    private var controls: View? = null
    private var pointer: View? = null
    private var seekBar: SeekBar? = null
    private var timeLabel: TextView? = null
    private var playButton: ImageButton? = null
    private var pointerX = 0f
    private var pointerY = 0f
    private var draggingSeek = false

    private val hideRunnable = Runnable { hideControls() }
    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (!draggingSeek) {
                val duration = p.duration.coerceAtLeast(0L)
                seekBar?.max = (duration / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seekBar?.progress = (p.currentPosition / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                timeLabel?.text = "${formatTime(p.currentPosition)}  /  ${formatTime(duration)}"
            }
            playButton?.setImageResource(if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
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

        val container = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val video = PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            player = this@CineVaultVideoPresentation.player
        }
        container.addView(video)
        container.addView(buildControlDock())
        container.addView(buildPointer())
        setContentView(container)
        root = container
        playerView = video
        handler.post(progressRunnable)
        onReady(ExternalPresentationHandle(video, visibleState, this))
    }

    private fun buildControlDock(): View {
        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(14), dp(24), dp(14))
            background = roundedBackground(Color.argb(220, 12, 12, 18), amber, dp(22).toFloat())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(46)
            }
        }
        val transport = LinearLayout(context).apply { gravity = Gravity.CENTER }
        transport.addView(iconButton(android.R.drawable.ic_media_rew) { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); scheduleHide() })
        playButton = iconButton(android.R.drawable.ic_media_pause) {
            if (player.isPlaying) player.pause() else player.play()
            scheduleHide()
        }.also { transport.addView(it) }
        transport.addView(iconButton(android.R.drawable.ic_media_ff) { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))); scheduleHide() })
        dock.addView(transport)
        seekBar = SeekBar(context).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(amber)
            thumbTintList = android.content.res.ColorStateList.valueOf(amber)
            layoutParams = LinearLayout.LayoutParams(-1, dp(34))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) { draggingSeek = true; handler.removeCallbacks(hideRunnable) }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) timeLabel?.text = "${formatTime(progress * 1000L)}  /  ${formatTime(player.duration)}" }
                override fun onStopTrackingTouch(seekBar: SeekBar) { player.seekTo(seekBar.progress * 1000L); draggingSeek = false; scheduleHide() }
            })
        }.also { dock.addView(it) }
        timeLabel = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
        }.also { dock.addView(it) }
        controls = dock
        return dock
    }

    private fun buildPointer(): View = View(context).apply {
        background = roundedBackground(Color.argb(115, 201, 167, 101), amber, dp(12).toFloat())
        visibility = View.GONE
        elevation = dp(8).toFloat()
        layoutParams = FrameLayout.LayoutParams(dp(24), dp(24))
        pointer = this
    }

    private fun iconButton(icon: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(amber)
        background = roundedBackground(Color.argb(130, 0, 0, 0), amber, dp(28).toFloat())
        contentDescription = when (icon) {
            android.R.drawable.ic_media_rew -> "Seek back 10 seconds"
            android.R.drawable.ic_media_ff -> "Seek forward 10 seconds"
            else -> "Play or pause"
        }
        layoutParams = LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8); marginEnd = dp(8) }
        setOnClickListener { action() }
    }

    fun showControls() {
        val r = root ?: return
        controls?.visibility = View.VISIBLE
        pointer?.visibility = View.VISIBLE
        visibleState.value = true
        if (pointerX == 0f && pointerY == 0f) {
            pointerX = r.width * 0.5f; pointerY = r.height * 0.68f
            updatePointerPosition()
        }
        scheduleHide()
    }

    fun hideControls() {
        controls?.visibility = View.GONE
        pointer?.visibility = View.GONE
        visibleState.value = false
        handler.removeCallbacks(hideRunnable)
    }

    fun movePointer(deltaX: Float, deltaY: Float) {
        val r = root ?: return
        showControls()
        pointerX = (pointerX + deltaX * 1.45f).coerceIn(0f, (r.width - dp(24)).toFloat())
        pointerY = (pointerY + deltaY * 1.45f).coerceIn(0f, (r.height - dp(24)).toFloat())
        updatePointerPosition()
    }

    private fun updatePointerPosition() {
        pointer?.x = pointerX
        pointer?.y = pointerY
    }

    fun clickPointer() {
        val r = root ?: return
        val x = pointerX + dp(12)
        val y = pointerY + dp(12)
        val now = android.os.SystemClock.uptimeMillis()
        r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        r.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0))
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 6_000L)
    }

    fun detachPlayer() {
        handler.removeCallbacksAndMessages(null)
        playerView?.player = null
        playerView = null
    }

    override fun onDisplayRemoved() { detachPlayer(); onDismissed(); super.onDisplayRemoved() }
    override fun onStop() { detachPlayer(); onDismissed(); super.onStop() }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
    private fun roundedBackground(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = radius
    }
    private fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val hours = total / 3600L; val minutes = (total % 3600L) / 60L; val seconds = total % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}
