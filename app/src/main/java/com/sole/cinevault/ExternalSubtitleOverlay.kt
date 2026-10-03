package com.sole.cinevault

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.subtitles.RawCue
import com.sole.cinevault.subtitles.SubtitleCueParser
import com.sole.cinevault.subtitles.SubtitleFormat
import com.sole.cinevault.subtitles.detectSubtitleFormat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Draws external (downloaded / local / generated / dual) subtitles ourselves instead of
 * attaching them to the video's MediaItem.
 *
 * Why: Media3 can only change an attached subtitle by rebuilding the video source, which
 * pauses playback, shows the buffering circle and restarts a little earlier. Here a subtitle
 * change is just "parse the file and feed new cues to the on-screen SubtitleView", so the
 * video is never touched: no pause, no spinner, no black frame.
 *
 * ExoPlayer's own subtitle track is kept switched off while this overlay owns the subtitles,
 * so an embedded track can never be drawn on top of the external one.
 * Embedded tracks keep working exactly as before when this overlay is not active.
 */
@OptIn(UnstableApi::class)
class ExternalSubtitleOverlay(
    private val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val subtitleViewProvider: () -> SubtitleView?,
) {
    private class TimedCue(val startMs: Long, val endMs: Long, val cue: Cue)

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    private var cues: List<TimedCue> = emptyList()
    private var maxDurationMs = 0L

    /** True from the moment an external subtitle is requested until it is cleared. */
    @Volatile
    private var owning = false
    private var hasLoaded = false
    private var visible = true
    private var lastShownKey = -2
    private var lastPushAtMs = 0L
    private var ticking = false

    /** A subtitle is loaded (visible or hidden) and owns subtitle rendering. */
    val hasContent: Boolean get() = owning && hasLoaded

    private val textGuard = object : Player.Listener {
        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            if (owning && !parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                disableExoText()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (ticking) main.postDelayed(this, TICK_MS)
        }
    }

    init {
        player.addListener(textGuard)
    }

    fun canHandle(uri: Uri): Boolean {
        val format = detectSubtitleFormat(uri)
        return format == SubtitleFormat.SRT || format == SubtitleFormat.VTT
    }

    /** Takes subtitle rendering over from ExoPlayer right now (before the file is read). */
    fun claimEarly() {
        owning = true
        disableExoText()
    }

    /**
     * Takes over subtitle rendering immediately (so no embedded track flashes up while the
     * file is read), loads [uri], and reports whether it could be shown.
     */
    fun show(uri: Uri, onResult: (Boolean) -> Unit) {
        owning = true
        disableExoText()
        loadJob?.cancel()
        loadJob = scope.launch {
            val parsed = withContext(Dispatchers.IO) { readCues(uri) }
            if (parsed.isEmpty()) {
                // Nothing usable: give rendering back so the caller can use the fallback.
                if (!hasLoaded) owning = false
                onResult(false)
                return@launch
            }
            cues = parsed
            maxDurationMs = parsed.maxOf { it.endMs - it.startMs }
            hasLoaded = true
            owning = true
            visible = true
            lastShownKey = -2
            startTicking()
            tick(force = true)
            onResult(true)
        }
    }

    /** Hide or show the loaded subtitle without forgetting it (the Subtitles on/off pill). */
    fun setVisible(value: Boolean) {
        visible = value
        tick(force = true)
    }

    /** Forget the subtitle and stop drawing. ExoPlayer's text selection is left as it is. */
    fun clear() {
        loadJob?.cancel()
        owning = false
        hasLoaded = false
        cues = emptyList()
        stopTicking()
        pushCues(emptyList(), -1)
    }

    fun release() {
        clear()
        player.removeListener(textGuard)
        scope.cancel()
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private fun disableExoText() {
        val current = trackSelector.parameters
        if (current.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) return
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        main.post(ticker)
    }

    private fun stopTicking() {
        ticking = false
        main.removeCallbacks(ticker)
    }

    private fun tick(force: Boolean = false) {
        if (!hasLoaded) return
        val position = player.currentPosition
        val key = if (visible) indexAt(position) else -1
        val now = SystemClock.elapsedRealtime()
        // Re-send every so often too, in case ExoPlayer cleared the view in the meantime.
        if (force || key != lastShownKey || now - lastPushAtMs > REFRESH_MS) {
            if (key < 0) {
                pushCues(emptyList(), key)
            } else {
                pushCues(cuesAround(position, key), key)
            }
        }
    }

    private fun pushCues(list: List<Cue>, key: Int) {
        lastShownKey = key
        lastPushAtMs = SystemClock.elapsedRealtime()
        subtitleViewProvider()?.setCues(list)
    }

    /** Index of the latest cue that has started and not yet ended, or -1. */
    private fun indexAt(positionMs: Long): Int {
        val list = cues
        if (list.isEmpty()) return -1
        var lo = 0
        var hi = list.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].startMs <= positionMs) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        var i = best
        val earliest = positionMs - maxDurationMs
        while (i >= 0 && list[i].startMs >= earliest) {
            if (list[i].endMs > positionMs) return i
            i--
        }
        return -1
    }

    /** The cue at [index] plus any other cues showing at the same moment (overlapping lines). */
    private fun cuesAround(positionMs: Long, index: Int): List<Cue> {
        val list = cues
        val result = ArrayList<Cue>(2)
        result.add(list[index].cue)
        var i = index - 1
        val earliest = positionMs - maxDurationMs
        while (i >= 0 && result.size < 3 && list[i].startMs >= earliest) {
            if (list[i].endMs > positionMs) result.add(0, list[i].cue)
            i--
        }
        return result
    }

    private fun readCues(uri: Uri): List<TimedCue> {
        return try {
            val text = when (uri.scheme) {
                "file", null -> File(uri.path ?: return emptyList()).readText(Charsets.UTF_8)
                else -> return emptyList()
            }
            SubtitleCueParser.parse(text).map { TimedCue(it.startMs, it.endMs, buildCue(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildCue(raw: RawCue): Cue {
        val html = raw.text.replace("\n", "<br>")
        val spanned: CharSequence =
            if (raw.text.contains('<')) {
                trimTrailing(SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)))
            } else {
                raw.text
            }
        return Cue.Builder().setText(spanned).build()
    }

    private fun trimTrailing(text: SpannableStringBuilder): Spanned {
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        if (end < text.length) text.delete(end, text.length)
        return text
    }

    private companion object {
        const val TICK_MS = 40L
        const val REFRESH_MS = 500L
    }
}
