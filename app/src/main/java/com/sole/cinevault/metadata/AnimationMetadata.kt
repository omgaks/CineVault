package com.sole.cinevault.metadata

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Persistent evidence/classification record used by the P5 Animation Intelligence layer.
 * F1 defines durable storage; F2 owns classification rules.
 */
@Entity(tableName = "animation_metadata")
data class AnimationMetadata(
    @PrimaryKey val videoPath: String,
    val tmdbId: Int? = null,
    val originalLanguage: String? = null,
    val keywords: List<String>? = null,
    val subtype: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
    val evidence: List<String>? = null,
    val classifierVersion: Int = 0,
    val updatedAt: Long = 0L,
)

@Dao
interface AnimationMetadataDao {
    @Query("SELECT * FROM animation_metadata WHERE videoPath = :videoPath")
    suspend fun getByPath(videoPath: String): AnimationMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: AnimationMetadata)

    @Query("DELETE FROM animation_metadata WHERE videoPath = :videoPath")
    suspend fun deleteByPath(videoPath: String)

    @Query("DELETE FROM animation_metadata")
    suspend fun clearAll()
}
