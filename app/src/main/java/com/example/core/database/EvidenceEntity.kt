package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_logs")
data class EvidenceEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val timestamp: Long,
  val formattedDate: String,
  val sha256Digest: String,
  val title: String,
  val threatLevel: String,
  val summary: String,
  val rawTelemetryJson: String
)
