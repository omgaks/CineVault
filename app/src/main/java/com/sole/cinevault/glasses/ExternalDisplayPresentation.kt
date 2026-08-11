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
import android.view.LayoutInflater
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
    fun clickPointer() = presentation.clickPointer()
    fun beginPointerDrag() = presentation.beginPointerDrag()
    fun endPointerDrag() = presentation.endPointerDrag()
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
    private var subtitlesDisabled = false
    private var sleepMinutes = 0
    private var sleepRunnable: Runnable? = null

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
        // A TextureView is deliberately used on the secondary display.
        // Several USB-C display stacks (including the tested RayNeo route)
        // accepted subtitle/canvas output from PlayerView while leaving its
        // default SurfaceView black. TextureView keeps the decoded video in
        // the Presentation's own window composition and avoids that failure.
        val video = (LayoutInflater.from(context)
            .inflate(R.layout.external_player_view, container, false) as PlayerView).apply {
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
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        header.addView(iconButton(android.R.drawable.ic_menu_revert) {
            hideControls()
            onBack()
        })
        header.addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(12) }
        })
        ratingText?.takeIf { it.isNotBlank() }?.let { score ->
            header.addView(TextView(context).apply {
                text = score
                setTextColor(amber)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(8), 0)
            })
        }
        dock.addView(header)
        val transport = LinearLayout(context).apply { gravity = Gravity.CENTER }
        transport.addView(iconButton(android.R.drawable.ic_media_rew) { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); scheduleHide() })
        playButton = iconButton(android.R.drawable.ic_media_pause) {
            if (player.isPlaying) player.pause() else player.play()
            scheduleHide()
        }.also { transport.addView(it) }
        transport.addView(iconButton(android.R.drawable.ic_media_ff) { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))); scheduleHide() })
        dock.addView(transport)
        val quickActions = LinearLayout(context).apply { gravity = Gravity.CENTER }
        quickActions.addView(textButton("SUB") {
            subtitlesDisabled = !subtitlesDisabled
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitlesDisabled)
                .build()
            scheduleHide()
        })
        quickActions.addView(textButton("SPEED") {
            val next = when {
                player.playbackParameters.speed < 1.24f -> 1.25f
                player.playbackParameters.speed < 1.49f -> 1.5f
                player.playbackParameters.speed < 1.99f -> 2f
                else -> 1f
            }
            player.setPlaybackSpeed(next)
            scheduleHide()
        })
        quickActions.addView(textButton("TIMER") {
            sleepMinutes = when (sleepMinutes) { 0 -> 30; 30 -> 60; 60 -> 90; else -> 0 }
            sleepRunnable?.let(handler::removeCallbacks)
            sleepRunnable = null
            if (sleepMinutes > 0) {
                sleepRunnable = Runnable { player.pause(); sleepMinutes = 0 }.also {
                    handler.postDelayed(it, sleepMinutes * 60_000L)
                }
            }
            scheduleHide()
        })
        dock.addView(quickActions)
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
            android.R.drawable.ic_menu_revert -> "Back"
            else -> "Play or pause"
        }
        layoutParams = LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8); marginEnd = dp(8) }
        setOnClickListener { action() }
    }

    private fun textButton(label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(amber)
        textSize = 12f
        gravity = Gravity.CENTER
        background = roundedBackground(Color.argb(130, 0, 0, 0), amber, dp(18).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(92), dp(42)).apply { marginStart = dp(6); marginEnd = dp(6) }
        isClickable = true
        isFocusable = true
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
        if (draggingSeek) {
            val now = android.os.SystemClock.uptimeMillis()
            r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, pointerX + dp(12), pointerY + dp(12), 0))
        }
    }

    private fun updatePointerPosition() {
        pointer?.x = pointerX
        pointer?.y = pointerY
    }

    fun clickPointer() {
        val r = root ?: return
        val x = pointerX + dp(12)
        val y = pointerY + dp(12)
        if (findClickableAt(r, x, y) == null) {
            hideControls()
            return
        }
        val now = android.os.SystemClock.uptimeMillis()
        r.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        r.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0))
        scheduleHide()
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
