package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * D2-10 — forwards Halo drag ownership through Android's normal touch pipeline.
 *
 * The existing CineVault scrollable/slider/gesture target remains authoritative.
 * Halo only reconstructs a canonical DOWN -> MOVE* -> UP/CANCEL stream after
 * HaloDragController has decided that the gesture is a drag.
 */
class HaloCanonicalDragDispatcher(
    private val rootView: View,
    private val syntheticDispatchGuard: HaloSyntheticDispatchGuard,
) {
    private var session: HaloDragDispatchSession? = null

    fun dispatch(event: HaloDragEvent): Boolean {
        if (rootView.width <= 0 || rootView.height <= 0) return false

        return when (event.type) {
            HaloDragEventType.DRAG_START -> start(event)
            HaloDragEventType.DRAG -> move(event.position)
            HaloDragEventType.DRAG_END -> finish(event.position, cancelled = false)
            HaloDragEventType.DRAG_CANCEL -> finish(event.position, cancelled = true)
        }
    }

    fun cancel() {
        val active = session ?: return
        dispatchMotion(
            action = MotionEvent.ACTION_CANCEL,
            position = active.lastPosition,
            downTimeMillis = active.downTimeMillis,
        )
        session = null
    }

    private fun start(event: HaloDragEvent): Boolean {
        cancel()

        val startPosition = HaloVector(
            x = event.position.x - event.delta.x,
            y = event.position.y - event.delta.y,
        )
        val downTime = SystemClock.uptimeMillis()

        session = HaloDragDispatchSession(
            downTimeMillis = downTime,
            lastPosition = event.position,
        )

        val downHandled = dispatchMotion(
            action = MotionEvent.ACTION_DOWN,
            position = startPosition,
            downTimeMillis = downTime,
        )
        val moveHandled = dispatchMotion(
            action = MotionEvent.ACTION_MOVE,
            position = event.position,
            downTimeMillis = downTime,
        )
        return downHandled || moveHandled
    }

    private fun move(position: HaloVector): Boolean {
        val active = session ?: return false
        active.lastPosition = position
        return dispatchMotion(
            action = MotionEvent.ACTION_MOVE,
            position = position,
            downTimeMillis = active.downTimeMillis,
        )
    }

    private fun finish(
        position: HaloVector,
        cancelled: Boolean,
    ): Boolean {
        val active = session ?: return false
        active.lastPosition = position

        val handled = dispatchMotion(
            action = if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
            position = position,
            downTimeMillis = active.downTimeMillis,
        )
        session = null
        return handled
    }

    private fun dispatchMotion(
        action: Int,
        position: HaloVector,
        downTimeMillis: Long,
    ): Boolean {
        val point = HaloTargetProjection.toPixels(
            position = position,
            widthPx = rootView.width,
            heightPx = rootView.height,
        )
        val now = SystemClock.uptimeMillis()
        val motionEvent = MotionEvent.obtain(
            downTimeMillis,
            now.coerceAtLeast(downTimeMillis),
            action,
            point.xPx,
            point.yPx,
            0,
        )

        return try {
            syntheticDispatchGuard.run {
                rootView.dispatchTouchEvent(motionEvent)
            }
        } finally {
            motionEvent.recycle()
        }
    }
}

internal data class HaloDragDispatchSession(
    val downTimeMillis: Long,
    var lastPosition: HaloVector,
)

class HaloSyntheticDispatchGuard {
    private var depth = 0

    fun isDispatching(): Boolean = depth > 0

    inline fun <T> run(block: () -> T): T {
        enter()
        return try {
            block()
        } finally {
            exit()
        }
    }

    @PublishedApi
    internal fun enter() {
        depth += 1
    }

    @PublishedApi
    internal fun exit() {
        depth = (depth - 1).coerceAtLeast(0)
    }
}

internal object HaloDragStartReconstruction {
    fun startOf(event: HaloDragEvent): HaloVector = HaloVector(
        x = event.position.x - event.delta.x,
        y = event.position.y - event.delta.y,
    )
}
