package com.sole.cinevault.collections

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class ShareEntry(val title: String, val year: String?, val owned: Boolean)

data class ShareLine(val text: String, val owned: Boolean)

data class ShareCardModel(
    val title: String,
    val headline: String,
    val progress: Float,
    val lines: List<ShareLine>,
    val more: Int,
)

/** Pure content of the share card. Only film titles and counts, nothing about the person or device. */
object ShareCardPlanner {
    const val MAX_LINES = 12

    fun model(title: String, entries: List<ShareEntry>, ownedCount: Int, releasedTotal: Int, hasFullList: Boolean): ShareCardModel {
        val shown = if (hasFullList) entries else entries.filter { it.owned }
        val lines = shown.take(MAX_LINES).map {
            ShareLine(it.title + (it.year?.let { y -> " ($y)" } ?: ""), it.owned)
        }
        val headline = when {
            !hasFullList -> "$ownedCount ${if (ownedCount == 1) "film" else "films"} in my library"
            releasedTotal > 0 && ownedCount >= releasedTotal -> "Complete: all $releasedTotal"
            else -> "I own $ownedCount of $releasedTotal"
        }
        val progress = if (hasFullList && releasedTotal > 0) (ownedCount.toFloat() / releasedTotal).coerceIn(0f, 1f) else 0f
        return ShareCardModel(title, headline, progress, lines, (shown.size - lines.size).coerceAtLeast(0))
    }
}

private const val W = 1080
private const val PAD = 72f

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end).trimEnd() + "…"
}

fun renderShareCard(model: ShareCardModel): Bitmap {
    val rowH = 88f
    val rows = model.lines.size + if (model.more > 0) 1 else 0
    val height = (430f + rows * rowH + 190f).toInt().coerceAtLeast(900)
    val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    bg.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF10131C.toInt(), 0xFF05060A.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W.toFloat(), height.toFloat(), bg)
    val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    glow.shader = RadialGradient(W * 0.8f, 0f, 760f, intArrayOf(0x66E8A020, 0x00E8A020), null, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W.toFloat(), height.toFloat(), glow)

    val maxText = W - PAD * 2
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F3EE.toInt(); textSize = 70f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    c.drawText(ellipsize(model.title, titlePaint, maxText), PAD, 170f, titlePaint)

    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC24D.toInt(); textSize = 46f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    c.drawText(ellipsize(model.headline, headPaint, maxText), PAD, 244f, headPaint)

    val barTop = 286f
    val barH = 22f
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF }
    c.drawRoundRect(RectF(PAD, barTop, W - PAD, barTop + barH), barH / 2, barH / 2, track)
    if (model.progress > 0f) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8A020.toInt() }
        c.drawRoundRect(RectF(PAD, barTop, PAD + (W - PAD * 2) * model.progress, barTop + barH), barH / 2, barH / 2, fill)
    }

    val textOwned = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F3EE.toInt(); textSize = 42f }
    val textMissing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA8A6A0.toInt(); textSize = 42f }
    val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8A020.toInt() }
    val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB07818.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; pathEffect = DashPathEffect(floatArrayOf(9f, 7f), 0f)
    }
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF05060A.toInt(); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    var y = 400f
    model.lines.forEach { line ->
        val cx = PAD + 22f
        val cy = y - 14f
        if (line.owned) {
            c.drawCircle(cx, cy, 22f, solid)
            val p = Path().apply { moveTo(cx - 9f, cy); lineTo(cx - 2f, cy + 8f); lineTo(cx + 10f, cy - 8f) }
            c.drawPath(p, tick)
        } else {
            c.drawCircle(cx, cy, 20f, dashed)
        }
        val paint = if (line.owned) textOwned else textMissing
        c.drawText(ellipsize(line.text, paint, maxText - 80f), PAD + 70f, y, paint)
        y += rowH
    }
    if (model.more > 0) {
        c.drawText("+ ${model.more} more", PAD + 70f, y, textMissing)
    }

    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC24D.toInt(); textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    c.drawText("CineVault", PAD, height - 96f, brand)
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B6A66.toInt(); textSize = 24f }
    c.drawText("Film data from TMDB. Not endorsed or certified by TMDB.", PAD, height - 52f, small)
    return bmp
}

/** Renders the card to the app cache and opens the Android share sheet. Returns false on failure. */
fun shareCollectionCard(context: Context, model: ShareCardModel): Boolean = runCatching {
    val bitmap = renderShareCard(model)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "collection-${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.shareprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share collection").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)
