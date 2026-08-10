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

// FIX: Phase 7 — cinevault_watch_history. Was a single SharedPreferences
// key holding one JSON array, capped at 60 entries and de-duplicated by
// videoPath in Kotlin (re-watching a video moved it to the front rather
// than adding a second entry) — migrated for consistency, since the
// explicit cap already made this a non-growth-risk store, same as
// Phases 5/6.
//
// videoPath as the primary key naturally replicates the original dedup
// behavior for free — inserting a row with an existing primary key
// replaces it via OnConflictStrategy.REPLACE, same net effect as the
// original's manual filterNot() before re-adding.
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoPath: String,
    val title: String,
    val watchedAt: Long
)

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAll(): List<WatchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: WatchHistoryEntity)

    // Used only by the one-time legacy-data migration.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<WatchHistoryEntity>)

    // Mirrors the original's .take(60) cap exactly — keeps only the 60
    // most recently watched entries, dropping anything older.
    @Query("DELETE FROM watch_history WHERE videoPath NOT IN (SELECT videoPath FROM watch_history ORDER BY watchedAt DESC LIMIT 60)")
    fun trimToMostRecent60()
}

@Database(entities = [WatchHistoryEntity::class], version = 1, exportSchema = false)
abstract class WatchHistoryDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile private var INSTANCE: WatchHistoryDatabase? = null

        fun getInstance(context: Context): WatchHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WatchHistoryDatabase::class.java,
                    "cinevault_watch_history.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
