package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedSignalDao {

  @Query("SELECT * FROM detected_signals ORDER BY timestamp DESC")
  fun getAllSignals(): Flow<List<DetectedSignalEntity>>

  @Query("SELECT * FROM detected_signals ORDER BY timestamp DESC LIMIT :limit")
  fun getRecentSignals(limit: Int = 100): Flow<List<DetectedSignalEntity>>

  @Query("SELECT * FROM detected_signals WHERE signalType = :type ORDER BY timestamp DESC")
  fun getSignalsByType(type: String): Flow<List<DetectedSignalEntity>>

  @Query("SELECT * FROM detected_signals WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
  fun getSignalsSince(sinceTimestamp: Long): Flow<List<DetectedSignalEntity>>

  @Query("SELECT COUNT(*) FROM detected_signals")
  fun getSignalCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSignal(signal: DetectedSignalEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSignals(signals: List<DetectedSignalEntity>)

  @Query("DELETE FROM detected_signals")
  suspend fun clearAllSignals()

  @Query("DELETE FROM detected_signals WHERE timestamp < :beforeTimestamp")
  suspend fun pruneOldSignals(beforeTimestamp: Long)
}
