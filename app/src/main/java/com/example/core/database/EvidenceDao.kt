package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {

  @Query("SELECT * FROM evidence_logs ORDER BY timestamp DESC")
  fun getAllEvidence(): Flow<List<EvidenceEntity>>

  @Query("SELECT * FROM evidence_logs WHERE id = :id")
  suspend fun getEvidenceById(id: Long): EvidenceEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEvidence(evidence: EvidenceEntity): Long

  @Query("DELETE FROM evidence_logs WHERE id = :id")
  suspend fun deleteEvidence(id: Long)

  @Query("DELETE FROM evidence_logs")
  suspend fun clearAll()
}
