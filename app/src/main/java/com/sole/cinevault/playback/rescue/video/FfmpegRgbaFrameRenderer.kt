package com.sole.cinevault.playback.rescue.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Android rendering adapter for a single decoded RGBA frame.
 *
 * The caller owns the Surface lifecycle and must invoke this on a background
 * thread. No decoding, playback scheduling, or Surface ownership is implied.
 */
internal object FfmpegRgbaFrameRenderer {
    fun render(frame: FfmpegContainerProbeLoader.RgbaFrame, surface: Surface) {
        require(surface.isValid) { "Video surface is unavailable" }
        require(frame.width > 0 && frame.height > 0)
        require(frame.rgba.size.toLong() == frame.width.toLong() * frame.height * 4L) {
            "Invalid RGBA frame length"
        }
        // Bitmap.Config.ARGB_8888 expects native RGBA bytes when populated
        // through copyPixelsFromBuffer on Android.
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame.rgba))
            val canvas: Canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                val bounds = Rect(0, 0, canvas.width, canvas.height)
                val scale = minOf(
                    canvas.width.toFloat() / frame.width,
                    canvas.height.toFloat() / frame.height,
                )
                val width = (frame.width * scale).toInt().coerceAtLeast(1)
                val height = (frame.height * scale).toInt().coerceAtLeast(1)
                val destination = Rect(
                    (bounds.width() - width) / 2,
                    (bounds.height() - height) / 2,
                    (bounds.width() + width) / 2,
                    (bounds.height() + height) / 2,
                )
                canvas.drawBitmap(bitmap, null, destination, null)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
