package com.sole.cinevault.metadata

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Remembers artwork a person replaced, so a change is never a one-way door.
 * Own tiny database on purpose: the main metadata database is left untouched.
 */
@Entity(tableName = "artwork_history")
data class ArtworkHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoPath: String,
    val kind: String,
    val url: String,
    val savedAt: Long,
)

@Dao
interface ArtworkHistoryDao {
    @Insert
    suspend fun insert(entry: ArtworkHistoryEntry)

    @Query("SELECT * FROM artwork_history WHERE videoPath = :videoPath AND kind = :kind ORDER BY savedAt DESC, id DESC")
    suspend fun forPathAndKind(videoPath: String, kind: String): List<ArtworkHistoryEntry>

    @Query("DELETE FROM artwork_history WHERE videoPath = :videoPath AND kind = :kind AND url = :url")
    suspend fun deleteUrl(videoPath: String, kind: String, url: String)

    @Query("DELETE FROM artwork_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM artwork_history WHERE videoPath = :videoPath")
    suspend fun clearPath(videoPath: String)
}

@Database(entities = [ArtworkHistoryEntry::class], version = 1, exportSchema = false)
abstract class ArtworkHistoryDatabase : RoomDatabase() {
    abstract fun dao(): ArtworkHistoryDao

    companion object {
        @Volatile private var instance: ArtworkHistoryDatabase? = null
        fun get(context: Context): ArtworkHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ArtworkHistoryDatabase::class.java,
                "cinevault_artwork_history.db"
            ).build().also { instance = it }
        }
    }
}

internal const val ARTWORK_HISTORY_KEEP = 8

/** The URL worth remembering when a choice changes from [previous] to [next], or null. */
internal fun urlToRemember(previous: String?, next: String?): String? =
    previous?.takeIf { it.isNotBlank() && it != next }

/** Newest first, no repeats, none of [exclude], at most [limit]. Pure. */
internal fun tidyHistory(
    urlsNewestFirst: List<String>,
    exclude: Set<String>,
    limit: Int = ARTWORK_HISTORY_KEEP,
): List<String> = urlsNewestFirst.filter { it.isNotBlank() && it !in exclude }.distinct().take(limit)

suspend fun rememberReplacedArtwork(
    context: Context,
    videoPath: String,
    kind: ArtworkKind,
    previous: String?,
    next: String?,
) = withContext(Dispatchers.IO) {
    val url = urlToRemember(previous, next) ?: return@withContext
    runCatching {
        val dao = ArtworkHistoryDatabase.get(context).dao()
        dao.deleteUrl(videoPath, kind.name, url)
        dao.insert(ArtworkHistoryEntry(videoPath = videoPath, kind = kind.name, url = url, savedAt = System.currentTimeMillis()))
        dao.forPathAndKind(videoPath, kind.name).drop(ARTWORK_HISTORY_KEEP).forEach { dao.deleteById(it.id) }
    }
}

/** Earlier choices that still exist (a deleted local file is skipped), newest first. */
suspend fun loadArtworkHistory(
    context: Context,
    videoPath: String,
    kind: ArtworkKind,
    currentUrl: String?,
): List<String> = withContext(Dispatchers.IO) {
    runCatching {
        val rows = ArtworkHistoryDatabase.get(context).dao().forPathAndKind(videoPath, kind.name)
        tidyHistory(
            rows.map { it.url }.filter { url ->
                if (url.startsWith("file:")) runCatching { File(Uri.parse(url).path.orEmpty()).exists() }.getOrDefault(false) else true
            },
            exclude = setOfNotNull(currentUrl)
        )
    }.getOrDefault(emptyList())
}
