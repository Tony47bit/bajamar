package com.pescasub.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun get(id: Long): LocationEntity?

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionWithDetails>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionWithDetails?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionWithDetails?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    /** Las capturas y fotos se borran en cascada. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertCatches(catches: List<CatchEntity>)

    @Query("DELETE FROM catches WHERE sessionId = :sessionId")
    suspend fun deleteCatches(sessionId: Long)

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE sessionId = :sessionId")
    suspend fun deletePhotos(sessionId: Long)
}

@Dao
interface ForecastCacheDao {
    @Query("SELECT * FROM forecast_cache WHERE cacheKey = :key")
    suspend fun get(key: String): ForecastCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ForecastCacheEntity)

    @Query("DELETE FROM forecast_cache")
    suspend fun clear()
}
