package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovered_devices")
data class DiscoveredDeviceEntity(
  @PrimaryKey
  val macOrId: String,
  val name: String,
  val vendor: String,
  val medium: String, // WIFI, BLE, ETHERNET, MDNS
  val ipAddress: String,
  val firstSeen: Long = System.currentTimeMillis(),
  val lastSeen: Long = System.currentTimeMillis(),
  val rssi: Int,
  val distanceMeters: Float,
  val roomProximityZone: String, // IMMEDIATE (<1.5m), SAME_ROOM (<4.5m), ADJACENT (<10m), FAR (>10m)
  val openPortsJson: String,
  val threatClassification: String,
  val riskScore: Int,
  val isSuspect: Boolean,
  val rawDetailsJson: String,
  // Automated AI Tagging & Inference Fields
  val aiTag: String = "Unknown", // Smartphone, IoT, Camera, Laptop/PC, Tracker/Beacon, Audio Bug, Router/AP, Unknown
  val aiConfidence: Float = 0.0f, // 0.0 to 1.0
  val aiInferenceReasoning: String = "",
  val aiTaggedAt: Long = 0L,
  val isAiVerified: Boolean = false
)

