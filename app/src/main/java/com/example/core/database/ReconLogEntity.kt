package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recon_logs")
data class ReconLogEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val formattedTime: String,
  val level: String, // INFO, WARN, ALERT, CRITICAL, AUDIT
  val subsystem: String, // AIRSPACE, SUBNET, KERNEL_AUDIT, BLE, ACOUSTIC, EM_FLUX, ROOM_ZONE
  val tag: String,
  val message: String,
  val metadataJson: String = ""
)
