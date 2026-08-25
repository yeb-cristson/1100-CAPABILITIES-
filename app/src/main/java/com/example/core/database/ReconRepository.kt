package com.example.core.database

import android.content.Context
import com.example.engine.AiInferenceTaggingEngine
import com.example.engine.AiTagResult
import com.example.engine.AiTaggingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReconRepository(
  private val database: AppDatabase,
  context: Context? = null
) {

  private val signalDao = database.detectedSignalDao()
  private val deviceDao = database.discoveredDeviceDao()
  private val logDao = database.reconLogDao()
  private val evidenceDao = database.evidenceDao()
  private val scope = CoroutineScope(Dispatchers.IO)

  val aiEngine: AiInferenceTaggingEngine? = context?.let { AiInferenceTaggingEngine(it, database) }
  val aiState: StateFlow<AiTaggingState>? = aiEngine?.state

  val allSignals: Flow<List<DetectedSignalEntity>> = signalDao.getAllSignals()
  val recentSignals: Flow<List<DetectedSignalEntity>> = signalDao.getRecentSignals(150)
  val signalCount: Flow<Int> = signalDao.getSignalCount()

  val allDevices: Flow<List<DiscoveredDeviceEntity>> = deviceDao.getAllDevices()
  val inRoomDevices: Flow<List<DiscoveredDeviceEntity>> = deviceDao.getInRoomDevices()
  val suspectDevices: Flow<List<DiscoveredDeviceEntity>> = deviceDao.getSuspectDevices()
  val inRoomDeviceCount: Flow<Int> = deviceDao.getInRoomDeviceCount()
  val totalDeviceCount: Flow<Int> = deviceDao.getTotalDeviceCount()

  fun getDevicesByAiTag(tag: String): Flow<List<DiscoveredDeviceEntity>> = deviceDao.getDevicesByAiTag(tag)
  fun getAiTagCount(tag: String): Flow<Int> = deviceDao.getCountByAiTag(tag)

  val allLogs: Flow<List<ReconLogEntity>> = logDao.getAllLogs()
  val recentLogs: Flow<List<ReconLogEntity>> = logDao.getRecentLogs(200)

  suspend fun logSignal(
    signalType: String,
    identifier: String,
    displayName: String,
    freqOrChan: String,
    rssi: Int,
    intensity: Float,
    threatScore: Int,
    protocol: String,
    metadataJson: String = ""
  ) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    signalDao.insertSignal(
      DetectedSignalEntity(
        formattedTime = time,
        signalType = signalType,
        identifier = identifier,
        displayName = displayName,
        frequencyOrChannel = freqOrChan,
        rssi = rssi,
        intensity = intensity,
        threatScore = threatScore,
        protocol = protocol,
        metadataJson = metadataJson
      )
    )
  }

  suspend fun upsertDevice(
    macOrId: String,
    name: String,
    vendor: String,
    medium: String,
    ipAddress: String,
    rssi: Int,
    distanceMeters: Float,
    roomZone: String,
    openPortsJson: String = "[]",
    threatClassification: String = "NOMINAL",
    riskScore: Int = 0,
    isSuspect: Boolean = false,
    rawDetailsJson: String = ""
  ) {
    val existing = deviceDao.getDeviceById(macOrId)
    val firstSeen = existing?.firstSeen ?: System.currentTimeMillis()
    
    // Check if we can perform on-device heuristic AI tagging immediately
    val resolvedName = if (name.isNotBlank() && name != "Unknown") name else (existing?.name ?: name)
    val resolvedVendor = if (vendor.isNotBlank() && vendor != "Unknown") vendor else (existing?.vendor ?: vendor)

    val preDevice = DiscoveredDeviceEntity(
      macOrId = macOrId,
      name = resolvedName,
      vendor = resolvedVendor,
      medium = medium,
      ipAddress = if (ipAddress.isNotBlank()) ipAddress else (existing?.ipAddress ?: ""),
      firstSeen = firstSeen,
      lastSeen = System.currentTimeMillis(),
      rssi = rssi,
      distanceMeters = distanceMeters,
      roomProximityZone = roomZone,
      openPortsJson = openPortsJson,
      threatClassification = threatClassification,
      riskScore = riskScore,
      isSuspect = isSuspect,
      rawDetailsJson = rawDetailsJson,
      aiTag = existing?.aiTag ?: "Unknown",
      aiConfidence = existing?.aiConfidence ?: 0.0f,
      aiInferenceReasoning = existing?.aiInferenceReasoning ?: "",
      aiTaggedAt = existing?.aiTaggedAt ?: 0L,
      isAiVerified = existing?.isAiVerified ?: false
    )

    val finalEntity = if (aiEngine != null && (preDevice.aiTag == "Unknown" || preDevice.aiConfidence < 0.5f)) {
      val inference = aiEngine.runOnDeviceHeuristicInference(preDevice)
      preDevice.copy(
        aiTag = inference.tag,
        aiConfidence = inference.confidence,
        aiInferenceReasoning = "[ON_DEVICE_AI] " + inference.reasoning,
        aiTaggedAt = System.currentTimeMillis(),
        isAiVerified = inference.confidence >= 0.8f
      )
    } else {
      preDevice
    }

    deviceDao.upsertDevice(finalEntity)
  }

  fun triggerBatchAiInference(onProgress: ((Int, Int) -> Unit)? = null) {
    aiEngine?.runBatchInference(onProgress)
  }

  suspend fun inferSingleDevice(macOrId: String): AiTagResult? {
    val dev = deviceDao.getDeviceById(macOrId) ?: return null
    return aiEngine?.inferAndPersistTag(dev)
  }

  suspend fun logSystemEvent(
    subsystem: String,
    tag: String,
    message: String,
    level: String = "INFO",
    metadataJson: String = ""
  ) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    logDao.insertLog(
      ReconLogEntity(
        formattedTime = time,
        level = level,
        subsystem = subsystem,
        tag = tag,
        message = message,
        metadataJson = metadataJson
      )
    )
  }

  suspend fun clearSignals() = signalDao.clearAllSignals()
  suspend fun clearDevices() = deviceDao.clearAllDevices()
  suspend fun clearLogs() = logDao.clearLogs()
}

