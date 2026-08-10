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

// FIX: Phase 5 — cinevault_scan_cache. Was a single SharedPreferences key
// holding one Set<String> (not per-item keys, so not the same growth risk
// as Phases 1-4) — migrated for consistency rather than a safety concern.
// Modeled as one row per scanned path rather than a single serialized-set
// column — a more natural Room fit for what is, at heart, a set of items.
@Entity(tableName = "scanned_paths")
data class ScannedPathEntity(@PrimaryKey val path: String)

@Dao
interface ScannedPathDao {
    @Query("SELECT path FROM scanned_paths")
    fun getAll(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(paths: List<ScannedPathEntity>)

    @Query("DELETE FROM scanned_paths")
    fun clear()
}

@Database(entities = [ScannedPathEntity::class], version = 1, exportSchema = false)
abstract class ScannedPathDatabase : RoomDatabase() {
    abstract fun scannedPathDao(): ScannedPathDao

    companion object {
        @Volatile private var INSTANCE: ScannedPathDatabase? = null

        fun getInstance(context: Context): ScannedPathDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScannedPathDatabase::class.java,
                    "cinevault_scanned_paths.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
