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
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sole.cinevault.metadata.TmdbCastMember

// FIX: Phase 4 of moving SharedPreferences-as-database usage to Room —
// cinevault_cast_cache, one SharedPreferences key per title (movie/show,
// not per raw video file, but the same unbounded-growth shape as Phase 1's
// metadata cache) storing a Gson-serialized cast list. Same real parse-
// failure risk as Phase 1 (unlike Phases 2/3's simple Long values) — but
// synchronous like Phases 2/3 rather than Phase 1's suspend approach,
// since loadCastCache's one real call site is inside a plain remember{}
// block in DetailScreen.kt, not a coroutine. The ORIGINAL SharedPreferences
// version already did this exact Gson parse synchronously on the main
// thread in that same remember{} block — Room's indexed single-row SELECT
// is the same or better cost than that was, not a new one, so there's
// nothing gained by restructuring the call site for suspend access here.
//
// cacheKey is an exact copy of the original SharedPreferences key format
// ("cast_${type}_${tmdbId}") rather than splitting it into separate
// tmdbId/type columns — keeps the one-time migration a direct one-to-one
// copy with nothing to parse apart, and DetailScreen.kt's own
// castCacheKey() already produces exactly this format for lookups.
@Entity(tableName = "cast_cache")
data class CastCacheEntity(
    @PrimaryKey val cacheKey: String,
    // FIX: was named "cast" — SQLite treats CAST as a reserved keyword
    // (the CAST(expr AS type) function), so "SELECT cast FROM ..." gets
    // misparsed as the start of that function call rather than a column
    // reference, causing a genuine SQL syntax error at KSP's query-
    // validation step. Renamed to sidestep the collision entirely rather
    // than escape the identifier with quotes/backticks everywhere it's
    // referenced.
    val castMembers: List<TmdbCastMember>
)

// Room needs an explicit converter for the cast list column — reuses
// Gson (already a dependency, already how this data was serialized
// before) rather than adding a second JSON library just for this.
class CastCacheTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromCastList(value: List<TmdbCastMember>): String = gson.toJson(value)

    @TypeConverter
    fun toCastList(value: String): List<TmdbCastMember> {
        return try {
            gson.fromJson(value, object : TypeToken<List<TmdbCastMember>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Dao
interface CastCacheDao {
    // FIX: was "SELECT castMembers FROM cast_cache WHERE ..." returning
    // List<TmdbCastMember> directly. Room's List<X> return-type convention
    // means "map MULTIPLE ROWS, each to one X" — which directly conflicts
    // with what this actually is: a SINGLE row whose one column already
    // holds a whole List<TmdbCastMember> via the TypeConverter above. Room
    // couldn't reconcile the two (it tried to construct a TmdbCastMember —
    // singular — out of the one castMembers column, and TmdbCastMember
    // needs four columns: id/name/character/profile_path). Mapping to the
    // full Entity instead sidesteps the ambiguity entirely — Entity
    // mapping is Room's actual strength — and the caller just reads the
    // one field it wants off the result.
    @Query("SELECT * FROM cast_cache WHERE cacheKey = :cacheKey")
    fun getEntity(cacheKey: String): CastCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CastCacheEntity)

    // Used only by the one-time legacy-data migration.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<CastCacheEntity>)
}

@Database(entities = [CastCacheEntity::class], version = 1, exportSchema = false)
@TypeConverters(CastCacheTypeConverters::class)
abstract class CastCacheDatabase : RoomDatabase() {
    abstract fun castCacheDao(): CastCacheDao

    companion object {
        @Volatile private var INSTANCE: CastCacheDatabase? = null

        fun getInstance(context: Context): CastCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CastCacheDatabase::class.java,
                    "cinevault_cast_cache.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
