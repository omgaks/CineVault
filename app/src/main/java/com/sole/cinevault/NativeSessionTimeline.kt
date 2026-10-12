package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline

/** One local film, exposing the native duration to MediaSession controllers. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NativeSessionTimeline(
    private val mediaItem: MediaItem,
    private val durationMs: Long,
) : Timeline() {
    private val uid: String = "native:${mediaItem.mediaId}:${mediaItem.localConfiguration?.uri}"
    override fun getWindowCount(): Int = 1
    override fun getPeriodCount(): Int = 1
    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        require(windowIndex == 0)
        return window.set(uid, mediaItem, null, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET,
            true, false, null, 0L, if (durationMs > 0L) durationMs * 1000L else C.TIME_UNSET, 0, 0, 0L)
    }
    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        require(periodIndex == 0)
        return period.set(if (setIds) uid else null, if (setIds) uid else null,
            0, if (durationMs > 0L) durationMs * 1000L else C.TIME_UNSET, 0L)
    }
    override fun getIndexOfPeriod(uid: Any): Int = if (this.uid == uid) 0 else C.INDEX_UNSET
    override fun getUidOfPeriod(periodIndex: Int): Any { require(periodIndex == 0); return uid }
}
