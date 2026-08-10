package com.sole.cinevault.metadata

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sole.cinevault.CastEntry

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

// exportSchema = false: this is a brand-new, version-1 database — nothing
// exists yet for Room to auto-migrate FROM within Room itself (the actual
// migration this phase cares about is one-time, INTO Room from the old
// cinevault_metadata_cache SharedPreferences file, handled separately in
// MetadataCache.kt). Worth revisiting once this schema needs its own
// first real version bump.
@Database(entities = [CachedVideoMetadata::class], version = 1, exportSchema = false)
@TypeConverters(MetadataTypeConverters::class)
abstract class CachedVideoMetadataDatabase : RoomDatabase() {
    abstract fun cachedVideoMetadataDao(): CachedVideoMetadataDao

    companion object {
        @Volatile private var INSTANCE: CachedVideoMetadataDatabase? = null

        fun getInstance(context: Context): CachedVideoMetadataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CachedVideoMetadataDatabase::class.java,
                    "cinevault_metadata.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
