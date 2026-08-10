package com.sole.cinevault.library

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
import androidx.room.Transaction

// FIX: the one deliberately-deferred store from the earlier Room
// migration pass — see the discussion at the time for why it was
// different from the other seven: a genuine documented main-thread
// hitch (see MainActivity.kt's own comment on loadLibraryCache, from
// before this migration) rather than just "for consistency."
//
// Modeled as one row per video plus a single small metadata row for the
// shared timestamp — NOT one giant serialized blob for the whole
// library, which is what actually caused that hitch (a single Gson
// parse of hundreds of videos' full metadata in one string). Each
// video's own JSON stays reasonably small individually; Room reads many
// small rows rather than one huge one. videoJson itself is still a Gson
// blob per row (not every field flattened into its own column) — that's
// a deliberate scope call, not an oversight: VideoWithMetadata has many
// fields, several of them nested lists, and flattening all of it into
// real columns is real additional complexity for a cache table that
// only ever needs "get everything" / "replace everything" access
// patterns, never queries filtering on individual metadata fields.
@Entity(tableName = "library_cache_videos")
data class LibraryCacheVideoEntity(
    @PrimaryKey val videoPath: String,
    val videoJson: String
)

// Singleton row (id is always 0) holding just the cache's timestamp —
// kept separate from the video rows themselves so "when was this cache
// last written" doesn't need scanning/aggregating the video table.
@Entity(tableName = "library_cache_meta")
data class LibraryCacheMetaEntity(
    @PrimaryKey val id: Int = 0,
    val timestamp: Long
)

@Dao
interface LibraryCacheDao {
    @Query("SELECT videoJson FROM library_cache_videos")
    suspend fun getAllVideoJson(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<LibraryCacheVideoEntity>)

    @Query("DELETE FROM library_cache_videos")
    suspend fun clearVideos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMeta(meta: LibraryCacheMetaEntity)

    @Query("SELECT * FROM library_cache_meta WHERE id = 0")
    suspend fun getMeta(): LibraryCacheMetaEntity?

    @Query("DELETE FROM library_cache_meta")
    suspend fun clearMeta()

    // Wrapped in a single transaction so a save is genuinely atomic —
    // matching the original's exact semantics (the whole cache gets
    // replaced together, never left in a half-old-half-new state if
    // something goes wrong partway through).
    @Transaction
    suspend fun replaceLibraryCache(videos: List<LibraryCacheVideoEntity>, timestamp: Long) {
        clearVideos()
        insertVideos(videos)
        setMeta(LibraryCacheMetaEntity(timestamp = timestamp))
    }

    @Transaction
    suspend fun clearLibraryCache() {
        clearVideos()
        clearMeta()
    }
}

@Database(entities = [LibraryCacheVideoEntity::class, LibraryCacheMetaEntity::class], version = 1, exportSchema = false)
abstract class LibraryCacheDatabase : RoomDatabase() {
    abstract fun libraryCacheDao(): LibraryCacheDao

    companion object {
        @Volatile private var INSTANCE: LibraryCacheDatabase? = null

        fun getInstance(context: Context): LibraryCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryCacheDatabase::class.java,
                    "cinevault_library_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
