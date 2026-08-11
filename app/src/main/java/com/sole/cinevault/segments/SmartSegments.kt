package com.sole.cinevault.segments

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.extractEpisodeInfo
import com.sole.cinevault.metadata.TmdbClient
import com.sole.cinevault.metadata.loadMetadataFetchEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class SegmentType { RECAP, INTRO, PREVIEW, COMMERCIAL, CREDITS, MID_CREDITS_SCENE, POST_CREDITS_SCENE }

data class SmartSegment(
    val type: SegmentType,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val confidence: Float = 0.9f,
    val userCorrected: Boolean = false
) {
    fun contains(positionMs: Long) = positionMs in startMs until endMs && endMs > startMs
}

data class SmartSegmentResult(
    val segments: List<SmartSegment> = emptyList(),
    val hasMidCreditsScene: Boolean = false,
    val hasPostCreditsScene: Boolean = false
)

@Entity(tableName = "smart_segments", primaryKeys = ["mediaKey", "type", "startMs"])
data class SmartSegmentEntity(
    val mediaKey: String,
    val type: String,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val confidence: Float,
    val userCorrected: Boolean,
    val fetchedAtMs: Long
)

@Dao
interface SmartSegmentDao {
    @Query("SELECT * FROM smart_segments WHERE mediaKey = :mediaKey ORDER BY startMs")
    suspend fun get(mediaKey: String): List<SmartSegmentEntity>

    @Query("DELETE FROM smart_segments WHERE mediaKey = :mediaKey AND userCorrected = 0")
    suspend fun deleteProviderRows(mediaKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<SmartSegmentEntity>)
}

@Database(entities = [SmartSegmentEntity::class], version = 1, exportSchema = true)
abstract class SmartSegmentDatabase : RoomDatabase() {
    abstract fun dao(): SmartSegmentDao

    companion object {
        @Volatile private var instance: SmartSegmentDatabase? = null
        fun get(context: Context): SmartSegmentDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SmartSegmentDatabase::class.java,
                "cinevault_smart_segments.db"
            ).build().also { instance = it }
        }
    }
}

/**
 * Resolves exact, community supplied segments and keeps playback independent
 * from provider outages by caching successful answers locally. Read access to
 * IntroDB is anonymous; CineVault never uploads timestamps from this class.
 */
class SmartSegmentRepository(private val context: Context) {
    private val dao = SmartSegmentDatabase.get(context).dao()
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun load(meta: VideoWithMetadata, durationMs: Long): SmartSegmentResult = withContext(Dispatchers.IO) {
        val episode = extractEpisodeInfo(meta.video.name)
        val mediaKey = buildMediaKey(meta, episode?.season, episode?.episode)
        val cached = dao.get(mediaKey)
        val freshEnough = cached.firstOrNull()?.fetchedAtMs?.let { System.currentTimeMillis() - it < CACHE_TTL_MS } == true

        if (!loadMetadataFetchEnabled(context)) {
            return@withContext SmartSegmentResult(segments = cached.mapNotNull { it.toModel() })
        }

        var flags = CreditFlags()
        if (meta.type.equals("movie", true) && meta.tmdbId != null && BuildConfig.TMDB_TOKEN.isNotBlank()) {
            flags = runCatching {
                val keywords = TmdbClient.api.getMovieDetails(BuildConfig.TMDB_TOKEN, meta.tmdbId).keywords?.keywords.orEmpty()
                    .mapNotNull { it.name?.lowercase()?.replace(" ", "") }
                CreditFlags(
                    mid = keywords.any { it == "duringcreditsstinger" || it == "midcreditsstinger" },
                    post = keywords.any { it == "aftercreditsstinger" || it == "postcreditsstinger" }
                )
            }.getOrDefault(CreditFlags())
        }

        val rows = if (freshEnough) cached else {
            val imdbId = resolveImdbId(meta)
            val downloaded = if (imdbId != null) fetchIntroDb(imdbId, episode?.season, episode?.episode, durationMs) else emptyList()
            if (downloaded.isNotEmpty()) {
                dao.deleteProviderRows(mediaKey)
                val entities = downloaded.map { it.toEntity(mediaKey) }
                dao.upsert(entities)
                dao.get(mediaKey)
            } else cached
        }

        SmartSegmentResult(
            segments = rows.mapNotNull { it.toModel() },
            hasMidCreditsScene = flags.mid,
            hasPostCreditsScene = flags.post
        )
    }

    private suspend fun resolveImdbId(meta: VideoWithMetadata): String? {
        val id = meta.tmdbId ?: return null
        if (BuildConfig.TMDB_TOKEN.isBlank()) return null
        return runCatching {
            if (meta.type.equals("tv", true)) TmdbClient.api.getTvExternalIds(BuildConfig.TMDB_TOKEN, id).imdb_id
            else TmdbClient.api.getMovieExternalIds(BuildConfig.TMDB_TOKEN, id).imdb_id
        }.getOrNull()?.takeIf { it.startsWith("tt") }
    }

    private fun fetchIntroDb(imdbId: String, season: Int?, episode: Int?, durationMs: Long): List<SmartSegment> {
        val url = "https://api.introdb.app/segments".toHttpUrl().newBuilder()
            .addQueryParameter("imdb_id", imdbId)
            .apply {
                if (season != null) addQueryParameter("season", season.toString())
                if (episode != null) addQueryParameter("episode", episode.toString())
            }.build()
        val request = Request.Builder().url(url).header("User-Agent", "CineVault/2.0").get().build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val root = JsonParser.parseString(response.body?.string().orEmpty())
                parseSegments(root, durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "IntroDB lookup failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseSegments(root: JsonElement, durationMs: Long): List<SmartSegment> {
        val candidates = when {
            root.isJsonArray -> root.asJsonArray.toList()
            root.isJsonObject -> listOf("segments", "results", "data").firstNotNullOfOrNull { key ->
                root.asJsonObject.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList()
            } ?: listOf(root)
            else -> emptyList()
        }
        return candidates.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val typeText = stringValue(obj, "segment_type", "type", "kind") ?: return@mapNotNull null
            val type = parseType(typeText) ?: return@mapNotNull null
            val start = secondsValue(obj, "start_sec", "start", "start_time") ?: return@mapNotNull null
            val end = secondsValue(obj, "end_sec", "end", "end_time") ?: return@mapNotNull null
            val startMs = (start * 1000).toLong().coerceAtLeast(0L)
            val endMs = (end * 1000).toLong().coerceAtMost(durationMs.takeIf { it > 1L } ?: Long.MAX_VALUE)
            if (endMs <= startMs || endMs - startMs < 1_000L) null
            else SmartSegment(type, startMs, endMs, "IntroDB", confidence = 0.92f)
        }.distinctBy { Triple(it.type, it.startMs, it.endMs) }.sortedBy { it.startMs }
    }

    private fun stringValue(obj: com.google.gson.JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> obj.get(key)?.takeIf { !it.isJsonNull }?.asString }

    private fun secondsValue(obj: com.google.gson.JsonObject, vararg keys: String): Double? {
        val value = stringValue(obj, *keys) ?: return null
        value.toDoubleOrNull()?.let { return it }
        val pieces = value.split(":").mapNotNull { it.toDoubleOrNull() }
        if (pieces.size !in 2..3) return null
        return pieces.reversed().mapIndexed { index, number -> number * listOf(1.0, 60.0, 3600.0)[index] }.sum()
    }

    private fun parseType(raw: String): SegmentType? = when (raw.lowercase().replace("-", "_").replace(" ", "_")) {
        "intro", "opening", "op" -> SegmentType.INTRO
        "recap" -> SegmentType.RECAP
        "preview", "outro_preview" -> SegmentType.PREVIEW
        "commercial", "ads", "ad" -> SegmentType.COMMERCIAL
        "credits", "credit", "outro", "ending", "ed" -> SegmentType.CREDITS
        "mid_credits", "mid_credits_scene", "duringcreditsstinger" -> SegmentType.MID_CREDITS_SCENE
        "post_credits", "post_credits_scene", "aftercreditsstinger" -> SegmentType.POST_CREDITS_SCENE
        else -> null
    }

    private fun buildMediaKey(meta: VideoWithMetadata, season: Int?, episode: Int?) =
        "${meta.type.lowercase()}:${meta.tmdbId ?: meta.video.path.hashCode()}:${season ?: 0}:${episode ?: 0}"

    private fun SmartSegment.toEntity(mediaKey: String) = SmartSegmentEntity(
        mediaKey, type.name, startMs, endMs, source, confidence, userCorrected, System.currentTimeMillis()
    )

    private fun SmartSegmentEntity.toModel() = runCatching {
        SmartSegment(SegmentType.valueOf(type), startMs, endMs, source, confidence, userCorrected)
    }.getOrNull()

    private data class CreditFlags(val mid: Boolean = false, val post: Boolean = false)

    companion object {
        private const val TAG = "SmartSegments"
        private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
