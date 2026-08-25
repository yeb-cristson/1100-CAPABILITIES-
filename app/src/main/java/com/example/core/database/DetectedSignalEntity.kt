package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detected_signals")
data class DetectedSignalEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val formattedTime: String,
  val signalType: String, // WIFI_BEACON, BLE_ADV, MDNS, SSDP, ACOUSTIC, EM_FLUX, OPTICAL_GLINT
  val identifier: String, // BSSID, BLE MAC, IP, Peak Freq
  val displayName: String,
  val frequencyOrChannel: String,
  val rssi: Int,
  val intensity: Float,
  val threatScore: Int,
  val protocol: String,
  val metadataJson: String
)
