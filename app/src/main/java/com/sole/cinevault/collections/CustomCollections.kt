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
import com.sole.cinevault.VideoWithMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Collections you make yourself. Own database, stored only on this device. */
@Entity(tableName = "custom_collection")
data class CustomCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Null = a hand-picked collection. Otherwise a [SmartRule] string. */
    val rule: String? = null,
    val createdAtMs: Long,
)

@Entity(tableName = "custom_collection_item", primaryKeys = ["collectionId", "videoPath"])
data class CustomCollectionItemEntity(
    val collectionId: Long,
    val videoPath: String,
)

@Dao
interface CustomCollectionDao {
    @Query("SELECT * FROM custom_collection ORDER BY name COLLATE NOCASE")
    fun collections(): Flow<List<CustomCollectionEntity>>

    @Query("SELECT * FROM custom_collection_item")
    fun items(): Flow<List<CustomCollectionItemEntity>>

    @Insert
    suspend fun insert(collection: CustomCollectionEntity): Long

    @Query("UPDATE custom_collection SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM custom_collection WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CustomCollectionItemEntity)

    @Query("DELETE FROM custom_collection_item WHERE collectionId = :id")
    suspend fun deleteItems(id: Long)
}

@Database(
    entities = [CustomCollectionEntity::class, CustomCollectionItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CustomCollectionDatabase : RoomDatabase() {
    abstract fun dao(): CustomCollectionDao

    companion object {
        @Volatile private var instance: CustomCollectionDatabase? = null
        fun get(context: Context): CustomCollectionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CustomCollectionDatabase::class.java,
                "cinevault_custom_collections.db"
            ).build().also { instance = it }
        }
    }
}

object CustomCollections {
    fun collections(context: Context): Flow<List<CustomCollectionEntity>> =
        CustomCollectionDatabase.get(context).dao().collections()

    /** collection id -> hand-picked video paths. */
    fun members(context: Context): Flow<Map<Long, Set<String>>> =
        CustomCollectionDatabase.get(context).dao().items().map { rows ->
            rows.groupBy({ it.collectionId }, { it.videoPath }).mapValues { it.value.toSet() }
        }

    suspend fun create(context: Context, name: String, rule: SmartRule?, paths: Set<String>): Long =
        withContext(Dispatchers.IO) {
            val dao = CustomCollectionDatabase.get(context).dao()
            val id = dao.insert(
                CustomCollectionEntity(name = name.trim().take(60), rule = rule?.takeUnless { it.isEmpty }?.encode(), createdAtMs = System.currentTimeMillis())
            )
            paths.forEach { dao.addItem(CustomCollectionItemEntity(id, it)) }
            id
        }

    suspend fun setMembers(context: Context, id: Long, paths: Set<String>) = withContext(Dispatchers.IO) {
        val dao = CustomCollectionDatabase.get(context).dao()
        dao.deleteItems(id)
        paths.forEach { dao.addItem(CustomCollectionItemEntity(id, it)) }
    }

    suspend fun rename(context: Context, id: Long, name: String) = withContext(Dispatchers.IO) {
        CustomCollectionDatabase.get(context).dao().rename(id, name.trim().take(60))
    }

    suspend fun delete(context: Context, id: Long) = withContext(Dispatchers.IO) {
        val dao = CustomCollectionDatabase.get(context).dao()
        dao.deleteItems(id)
        dao.delete(id)
    }

    /** The films a collection currently holds, drawn from [videos] (pass only visible ones). */
    fun resolve(entity: CustomCollectionEntity, memberPaths: Set<String>, videos: List<VideoWithMetadata>): List<VideoWithMetadata> {
        val rule = SmartRule.decode(entity.rule)
        return if (rule != null) {
            videos.filter { v ->
                (v.type == "movie" || v.type == "tv") &&
                    rule.matches(v.genres, if (v.type == "movie") v.subtitle.take(4).toIntOrNull() else null, v.rating, v.director)
            }
        } else {
            videos.filter { it.video.path in memberPaths }
        }.sortedBy { it.title.lowercase() }
    }
}
