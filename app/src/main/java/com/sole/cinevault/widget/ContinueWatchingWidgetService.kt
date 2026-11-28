package com.sole.cinevault.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import coil.Coil
import coil.request.ImageRequest
import com.sole.cinevault.R
import com.sole.cinevault.VideoDurationDatabase
import com.sole.cinevault.mediaBadgesFromName
import com.sole.cinevault.library.PlaybackPositionDatabase
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.isRestrictedFolderItem
import com.sole.cinevault.library.isSecretItem
import com.sole.cinevault.library.loadLibraryCache
import com.sole.cinevault.library.loadSecretFolderPaths
import com.sole.cinevault.library.loadSecretVideoPaths
import kotlinx.coroutines.runBlocking

class ContinueWatchingWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<WidgetFilm> = emptyList()

        override fun onCreate() = Unit

        // Runs on a background thread, so reading the library and posters here is fine.
        override fun onDataSetChanged() {
            items = runCatching { runBlocking { loadFilms(context) } }.getOrDefault(emptyList())
        }

        override fun onDestroy() { items = emptyList() }
        override fun getCount() = items.size
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true
        override fun getItemId(position: Int) = items.getOrNull(position)?.path.hashCode().toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val film = items.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.widget_continue_watching_item)
            return RemoteViews(context.packageName, R.layout.widget_continue_watching_item).apply {
                if (film.poster != null) {
                    setImageViewBitmap(R.id.widget_poster, film.poster)
                } else {
                    setImageViewResource(R.id.widget_poster, R.drawable.widget_poster_placeholder)
                }
                setTextViewText(R.id.widget_title, film.title)
                setViewVisibility(R.id.widget_badge_3d, if (film.is3d) android.view.View.VISIBLE else android.view.View.GONE)
                setProgressBar(R.id.widget_progress, 1000, (film.progress * 1000).toInt(), false)
                setOnClickFillInIntent(R.id.widget_item_root, Intent().putExtra(WidgetPlayRequest.EXTRA_PATH, film.path))
            }
        }
    }
}

private class WidgetFilm(val path: String, val title: String, val poster: Bitmap?, val is3d: Boolean, val progress: Float)

private const val MAX_FILMS = 8
private const val CARD_W = 480
private const val CARD_H = 270
private const val CARD_RADIUS_PX = 36f

/** Films you have started, never including anything from the Secret or Restricted folders. */
private suspend fun loadFilms(context: Context): List<WidgetFilm> {
    val library = loadLibraryCache(context)?.videos.orEmpty()
    val secretPaths = loadSecretVideoPaths(context)
    val secretFolders = loadSecretFolderPaths(context)
    val positions = PlaybackPositionDatabase.getInstance(context).playbackPositionDao().getAll()
        .associate { it.videoPath to it.positionMs }
    val durations = VideoDurationDatabase.getInstance(context).videoDurationDao().getAll()
        .associate { it.videoPath to it.durationMs }

    val started: List<VideoWithMetadata> = library.filter { item ->
        val path = runCatching { item.video.path }.getOrNull().orEmpty()
        path.isNotBlank() &&
            (positions[path] ?: 0L) > 15_000L &&
            !isSecretItem(item, secretPaths, secretFolders) &&
            !isRestrictedFolderItem(item)
    }.take(MAX_FILMS)

    return started.map { item ->
        val path = item.video.path
        val duration = durations[path] ?: 0L
        val progress = if (duration > 0) ((positions[path] ?: 0L).toFloat() / duration).coerceIn(0f, 1f) else 0f
        WidgetFilm(
            path = path,
            title = item.title.ifBlank { item.video.name },
            poster = loadCard(context, item.backdropUrl ?: item.episodeStill ?: item.posterUrl),
            is3d = "3D" in mediaBadgesFromName(item.video.name),
            progress = progress,
        )
    }
}

// Landscape card art (backdrop, else the poster cropped to fit) with rounded corners, kept small so
// every widget image fits through the system's size limit.
private suspend fun loadCard(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(CARD_W, CARD_H)
            .scale(coil.size.Scale.FILL)
            .allowHardware(false)
            .build()
        val source = (Coil.imageLoader(context).execute(request).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        source?.let { roundedCard(it) }
    }.getOrNull()
}

private fun roundedCard(source: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val scale = maxOf(CARD_W / source.width.toFloat(), CARD_H / source.height.toFloat())
    val w = source.width * scale
    val h = source.height * scale
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate((CARD_W - w) / 2f, (CARD_H - h) / 2f)
    }
    val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        .apply { setLocalMatrix(matrix) }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    android.graphics.Canvas(out).drawRoundRect(android.graphics.RectF(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat()), CARD_RADIUS_PX, CARD_RADIUS_PX, paint)
    return out
}
