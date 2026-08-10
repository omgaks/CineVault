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

// FIX: Phase 6 — cinevault_favorites. Same shape and same reasoning as
// Phase 5's scan cache — a single Set<String>, migrated for consistency
// rather than a growth-risk concern.
@Entity(tableName = "favorite_paths")
data class FavoritePathEntity(@PrimaryKey val path: String)

@Dao
interface FavoritePathDao {
    @Query("SELECT path FROM favorite_paths")
    fun getAll(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(paths: List<FavoritePathEntity>)

    @Query("DELETE FROM favorite_paths")
    fun clear()
}

@Database(entities = [FavoritePathEntity::class], version = 1, exportSchema = false)
abstract class FavoritePathDatabase : RoomDatabase() {
    abstract fun favoritePathDao(): FavoritePathDao

    companion object {
        @Volatile private var INSTANCE: FavoritePathDatabase? = null

        fun getInstance(context: Context): FavoritePathDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FavoritePathDatabase::class.java,
                    "cinevault_favorite_paths.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
