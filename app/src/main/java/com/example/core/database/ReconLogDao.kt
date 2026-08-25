package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReconLogDao {

  @Query("SELECT * FROM recon_logs ORDER BY timestamp DESC")
  fun getAllLogs(): Flow<List<ReconLogEntity>>

  @Query("SELECT * FROM recon_logs ORDER BY timestamp DESC LIMIT :limit")
  fun getRecentLogs(limit: Int = 200): Flow<List<ReconLogEntity>>

  @Query("SELECT * FROM recon_logs WHERE subsystem = :subsystem ORDER BY timestamp DESC LIMIT :limit")
  fun getLogsBySubsystem(subsystem: String, limit: Int = 100): Flow<List<ReconLogEntity>>

  @Query("SELECT * FROM recon_logs WHERE level IN ('ALERT', 'CRITICAL') ORDER BY timestamp DESC")
  fun getCriticalLogs(): Flow<List<ReconLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: ReconLogEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLogs(logs: List<ReconLogEntity>)

  @Query("DELETE FROM recon_logs")
  suspend fun clearLogs()

  @Query("DELETE FROM recon_logs WHERE timestamp < :beforeTimestamp")
  suspend fun pruneLogs(beforeTimestamp: Long)
}
