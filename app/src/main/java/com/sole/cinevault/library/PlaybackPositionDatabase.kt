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

// FIX: Phase 3 of moving SharedPreferences-as-database usage to Room —
// playback_memory, one SharedPreferences key per video (same shape as
// Phase 1's metadata cache and Phase 2's duration cache). Same synchronous,
// allowMainThreadQueries() approach as Phase 2 rather than Phase 1's
// suspend-based one — savePlaybackPosition()/loadPlaybackPosition() are
// called directly inside composable bodies in several places (Screens.kt's
// LazyRow items, DetailScreen.kt/TvShowDetailScreen.kt's remember blocks),
// not from suspend functions, and this is genuinely the higher-traffic
// write path of everything migrated so far — it saves on a periodic timer
// during active playback. A single indexed-primary-key upsert is fast
// enough on the main thread that this doesn't introduce playback jank;
// restructuring every call site for suspend access would have been real
// added complexity for no meaningful safety gain here.
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val videoPath: String,
    val positionMs: Long
)

@Dao
interface PlaybackPositionDao {
    @Query("SELECT positionMs FROM playback_positions WHERE videoPath = :videoPath")
    fun getPosition(videoPath: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: PlaybackPositionEntity)

    // Used only by the one-time legacy-data migration.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<PlaybackPositionEntity>)

    @Query("DELETE FROM playback_positions WHERE videoPath = :videoPath")
    fun delete(videoPath: String)

    // FIX: clearPlaybackFolderPositions' original SharedPreferences
    // version used a plain Kotlin startsWith(folderPath) — an exact,
    // literal prefix match with no wildcard semantics. A naive SQL LIKE
    // using folderPath directly would NOT be equivalent — SQLite's LIKE
    // treats '_' as "match any single character" and '%' as "match any
    // sequence," and '_' specifically is extremely common in real
    // filenames/folder names. That would silently over-match (e.g. a
    // folder named "My_Movies" would also match "MyXMovies",
    // "MyYMovies", anything with any character where the underscore
    // was), deleting playback positions for videos that were never
    // actually inside the folder being cleared. Getting every path and
    // filtering with the same startsWith() in Kotlin exactly replicates
    // the original semantics rather than introducing a new, different
    // bug — this only runs when a folder gets marked secret, an
    // infrequent, deliberate action, not a hot path worth optimizing at
    // the cost of correctness.
    @Query("SELECT videoPath FROM playback_positions")
    fun getAllPaths(): List<String>
}

@Database(entities = [PlaybackPositionEntity::class], version = 1, exportSchema = false)
abstract class PlaybackPositionDatabase : RoomDatabase() {
    abstract fun playbackPositionDao(): PlaybackPositionDao

    companion object {
        @Volatile private var INSTANCE: PlaybackPositionDatabase? = null

        fun getInstance(context: Context): PlaybackPositionDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaybackPositionDatabase::class.java,
                    "cinevault_playback_positions.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
