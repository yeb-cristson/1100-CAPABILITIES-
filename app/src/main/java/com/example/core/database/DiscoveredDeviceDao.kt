package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveredDeviceDao {

  @Query("SELECT * FROM discovered_devices ORDER BY lastSeen DESC")
  fun getAllDevices(): Flow<List<DiscoveredDeviceEntity>>

  @Query("SELECT * FROM discovered_devices ORDER BY lastSeen DESC")
  suspend fun getAllDevicesSnapshot(): List<DiscoveredDeviceEntity>

  @Query("SELECT * FROM discovered_devices WHERE roomProximityZone = :zone ORDER BY distanceMeters ASC")
  fun getDevicesByZone(zone: String): Flow<List<DiscoveredDeviceEntity>>

  @Query("SELECT * FROM discovered_devices WHERE roomProximityZone IN ('IMMEDIATE', 'SAME_ROOM') ORDER BY distanceMeters ASC")
  fun getInRoomDevices(): Flow<List<DiscoveredDeviceEntity>>

  @Query("SELECT * FROM discovered_devices WHERE isSuspect = 1 ORDER BY lastSeen DESC")
  fun getSuspectDevices(): Flow<List<DiscoveredDeviceEntity>>

  @Query("SELECT COUNT(*) FROM discovered_devices WHERE roomProximityZone IN ('IMMEDIATE', 'SAME_ROOM')")
  fun getInRoomDeviceCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM discovered_devices")
  fun getTotalDeviceCount(): Flow<Int>

  @Query("SELECT * FROM discovered_devices WHERE aiTag = :tag ORDER BY lastSeen DESC")
  fun getDevicesByAiTag(tag: String): Flow<List<DiscoveredDeviceEntity>>

  @Query("SELECT * FROM discovered_devices WHERE aiTag = 'Unknown' OR aiConfidence < 0.5")
  suspend fun getUntaggedOrLowConfidenceDevices(): List<DiscoveredDeviceEntity>

  @Query("UPDATE discovered_devices SET aiTag = :aiTag, aiConfidence = :aiConfidence, aiInferenceReasoning = :aiReasoning, aiTaggedAt = :aiTaggedAt, isAiVerified = :isAiVerified WHERE macOrId = :macOrId")
  suspend fun updateAiTag(
    macOrId: String,
    aiTag: String,
    aiConfidence: Float,
    aiReasoning: String,
    aiTaggedAt: Long,
    isAiVerified: Boolean
  )

  @Query("SELECT COUNT(*) FROM discovered_devices WHERE aiTag = :tag")
  fun getCountByAiTag(tag: String): Flow<Int>

  @Query("SELECT * FROM discovered_devices WHERE macOrId = :id LIMIT 1")
  suspend fun getDeviceById(id: String): DiscoveredDeviceEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDevice(device: DiscoveredDeviceEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDevices(devices: List<DiscoveredDeviceEntity>)

  @Query("DELETE FROM discovered_devices WHERE macOrId = :id")
  suspend fun deleteDevice(id: String)

  @Query("DELETE FROM discovered_devices")
  suspend fun clearAllDevices()
}
