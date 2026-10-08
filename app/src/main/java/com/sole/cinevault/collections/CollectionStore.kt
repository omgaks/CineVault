package com.sole.cinevault.collections

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
import kotlinx.coroutines.flow.Flow

/**
 * Collections V2 storage. Its own small database (like the other CineVault
 * Room databases) so adding it can never touch existing user data or require
 * a migration of any existing database.
 */
@Entity(tableName = "collection_cache")
data class CollectionCacheEntity(
    @PrimaryKey val collectionId: Int,
    val name: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    /** JSON array written/read by org.json (no reflection, safe under R8). */
    val partsJson: String,
    val fetchedAtMs: Long
)

@Entity(tableName = "collection_wantlist")
data class WantlistEntity(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val releaseDate: String?,
    val collectionId: Int?,
    val addedAtMs: Long
)

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collection_cache WHERE collectionId = :collectionId")
    suspend fun cached(collectionId: Int): CollectionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entity: CollectionCacheEntity)

    @Query("SELECT tmdbId FROM collection_wantlist")
    fun wantlistIds(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWanted(entity: WantlistEntity)

    @Query("DELETE FROM collection_wantlist WHERE tmdbId = :tmdbId")
    suspend fun removeWanted(tmdbId: Int)
}

@Database(
    entities = [CollectionCacheEntity::class, WantlistEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun dao(): CollectionDao

    companion object {
        @Volatile private var instance: CollectionDatabase? = null

        fun get(context: Context): CollectionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CollectionDatabase::class.java,
                "cinevault_collections.db"
            ).build().also { instance = it }
        }
    }
}
