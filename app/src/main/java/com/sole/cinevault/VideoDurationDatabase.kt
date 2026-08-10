package com.sole.cinevault

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

// FIX: Phase 2 of moving SharedPreferences-as-database usage to Room —
// cinevault_durations, one SharedPreferences key per video (same growing-
// key-count shape as Phase 1's metadata cache, though lower severity here
// since each value is just a Long rather than a JSON blob — no parse-
// failure crash risk, but every single write still rewrites the WHOLE
// SharedPreferences file once the library is large, same underlying cost).
//
// Deliberately synchronous, unlike Phase 1's suspend-based access —
// loadDuration()/saveDuration() are called directly inside composable
// bodies in several places (Screens.kt's LazyRow items, for one), not
// from suspend functions. Restructuring every one of those call sites to
// use LaunchedEffect/produceState for a simple Long lookup felt like
// disproportionate complexity for what this store actually needs.
// allowMainThreadQueries() is a real, Room-supported configuration for
// exactly this shape of access — a single indexed-primary-key lookup in
// a small table is fast enough that main-thread blocking is a non-issue
// in practice, and it means every existing call site stays completely
// unchanged: same non-suspend function signatures calling straight
// through to Room, same as they called straight through to
// SharedPreferences before.
@Entity(tableName = "video_durations")
data class VideoDurationEntity(
    @PrimaryKey val videoPath: String,
    val durationMs: Long
)

@Dao
interface VideoDurationDao {
    @Query("SELECT durationMs FROM video_durations WHERE videoPath = :videoPath")
    fun getDuration(videoPath: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: VideoDurationEntity)

    // Used only by the one-time legacy-data migration.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<VideoDurationEntity>)
}

@Database(entities = [VideoDurationEntity::class], version = 1, exportSchema = false)
abstract class VideoDurationDatabase : RoomDatabase() {
    abstract fun videoDurationDao(): VideoDurationDao

    companion object {
        @Volatile private var INSTANCE: VideoDurationDatabase? = null

        fun getInstance(context: Context): VideoDurationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoDurationDatabase::class.java,
                    "cinevault_durations.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
