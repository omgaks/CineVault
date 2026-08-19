package com.sole.cinevault.metadata

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sole.cinevault.CastEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AUTOMATIC_ARTWORK_RETRY_COOLDOWN_MS = 24L * 60L * 60L * 1000L

@Entity(tableName = "artwork_preferences")
data class ArtworkPreference(
    @PrimaryKey val videoPath: String,
    val manualPosterUrl: String? = null,
    val manualBackdropUrl: String? = null,
    val lastAutomaticAttemptAt: Long = 0L
)

@Dao
interface CachedVideoMetadataDao {
    @Query("SELECT * FROM cached_video_metadata WHERE videoPath = :videoPath")
    suspend fun getByPath(videoPath: String): CachedVideoMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: CachedVideoMetadata)

    // Used only by the one-time legacy-data migration (see
    // ensureMigratedToRoom in MetadataCache.kt) — inserting potentially
    // hundreds of rows one at a time would be needlessly slow.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metadata: List<CachedVideoMetadata>)
}

@Dao
interface ArtworkPreferenceDao {
    @Query("SELECT * FROM artwork_preferences WHERE videoPath = :videoPath")
    suspend fun getByPath(videoPath: String): ArtworkPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: ArtworkPreference)

    @Query("DELETE FROM artwork_preferences WHERE videoPath = :videoPath")
    suspend fun deleteByPath(videoPath: String)

    @Query("DELETE FROM artwork_preferences")
    suspend fun clearAll()
}

suspend fun canAttemptAutomaticArtwork(context: Context, videoPath: String): Boolean =
    withContext(Dispatchers.IO) {
        val saved = CachedVideoMetadataDatabase.getInstance(context)
            .artworkPreferenceDao()
            .getByPath(videoPath)
        saved == null || System.currentTimeMillis() - saved.lastAutomaticAttemptAt >= AUTOMATIC_ARTWORK_RETRY_COOLDOWN_MS
    }

suspend fun recordAutomaticArtworkAttempt(context: Context, videoPath: String) =
    withContext(Dispatchers.IO) {
        val dao = CachedVideoMetadataDatabase.getInstance(context).artworkPreferenceDao()
        val existing = dao.getByPath(videoPath)
        dao.upsert(
            (existing ?: ArtworkPreference(videoPath = videoPath)).copy(
                lastAutomaticAttemptAt = System.currentTimeMillis()
            )
        )
    }

// Room only understands primitive-ish column types natively —
// List<String>?/List<CastEntry>? need explicit conversion to/from a
// single stored column. Reuses Gson (already a dependency, already used
// everywhere else in this file) rather than adding a second JSON library
// just for this.
class MetadataTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        return try {
            gson.fromJson(value, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromCastEntryList(value: List<CastEntry>?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toCastEntryList(value: String?): List<CastEntry>? {
        if (value == null) return null
        return try {
            gson.fromJson(value, object : TypeToken<List<CastEntry>>() {}.type)
        } catch (_: Exception) {
            null
        }
    }
}

// Version 2 adds a separate artwork-preferences table. Keeping manual artwork
// choices and retry timestamps separate from cached metadata means ordinary
// metadata saves cannot accidentally erase a person's chosen images.
@Database(
    entities = [CachedVideoMetadata::class, ArtworkPreference::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(MetadataTypeConverters::class)
abstract class CachedVideoMetadataDatabase : RoomDatabase() {
    abstract fun cachedVideoMetadataDao(): CachedVideoMetadataDao
    abstract fun artworkPreferenceDao(): ArtworkPreferenceDao

    companion object {
        @Volatile private var INSTANCE: CachedVideoMetadataDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `artwork_preferences` (
                        `videoPath` TEXT NOT NULL,
                        `manualPosterUrl` TEXT,
                        `manualBackdropUrl` TEXT,
                        `lastAutomaticAttemptAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoPath`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): CachedVideoMetadataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CachedVideoMetadataDatabase::class.java,
                    "cinevault_metadata.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
