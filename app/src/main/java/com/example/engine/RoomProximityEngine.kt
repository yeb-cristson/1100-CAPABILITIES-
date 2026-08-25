package com.example.engine

import android.content.Context
import com.example.core.database.ReconRepository
import com.example.core.hub.ReconHub
import com.example.core.model.ThreatLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ProximityZone(val label: String, val maxDistanceMeters: Float, val colorHex: Long) {
  IMMEDIATE("IMMEDIATE (<1.5m)", 1.5f, 0xFFFF2A2A),
  SAME_ROOM("SAME ROOM (<4.5m)", 4.5f, 0xFFFF9800),
  PERIMETER("PERIMETER (<10m)", 10.0f, 0xFF00E5FF),
  FAR_FIELD("FAR FIELD (>10m)", 50.0f, 0xFF71717A)
}

data class RoomDeviceProfile(
  val id: String,
  val displayName: String,
  val macOrIp: String,
  val vendor: String,
  val medium: String, // WIFI, BLE, SUBNET, MDNS
  val rssi: Int,
  val distanceMeters: Float,
  val zone: ProximityZone,
  val confidencePercent: Int,
  val isSuspectOrCamera: Boolean,
  val aiTag: String = "Unknown",
  val aiConfidence: Float = 0.0f,
  val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class RoomAuditSummary(
  val totalDetectedCount: Int = 0,
  val inRoomCount: Int = 0,
  val immediateCount: Int = 0,
  val perimeterCount: Int = 0,
  val suspectCount: Int = 0,
  val estimatedRoomRfDensity: String = "LOW", // LOW, MODERATE, HIGH, SATURATED
  val threatLevel: ThreatLevel = ThreatLevel.NOMINAL,
  val lastScanTimestamp: Long = System.currentTimeMillis()
)

class RoomProximityEngine(
  private val context: Context,
  private val repository: ReconRepository
) {
  private val scope = CoroutineScope(Dispatchers.Default + Job())
  private var pollingJob: Job? = null

  private val _devices = MutableStateFlow<List<RoomDeviceProfile>>(emptyList())
  val devices: StateFlow<List<RoomDeviceProfile>> = _devices.asStateFlow()

  private val _summary = MutableStateFlow(RoomAuditSummary())
  val summary: StateFlow<RoomAuditSummary> = _summary.asStateFlow()

  private val _selectedRangeLimit = MutableStateFlow(4.5f) // Default room radius
  val selectedRangeLimit: StateFlow<Float> = _selectedRangeLimit.asStateFlow()

  fun setRangeFilter(meters: Float) {
    _selectedRangeLimit.value = meters
    recompute()
  }

  fun start() {
    pollingJob?.cancel()
    pollingJob = scope.launch {
      while (isActive) {
        syncWithReconHub()
        delay(1200)
      }
    }
  }

  fun stop() {
    pollingJob?.cancel()
  }

  private fun syncWithReconHub() {
    val hub = try { ReconHub.getInstance() } catch (e: Exception) { return }
    val rfs = hub.rfDevices.value
    val trackers = hub.bleTrackers.value
    val hosts = hub.subnetHosts.value
    val protos = hub.protoServices.value

    val profiles = mutableListOf<RoomDeviceProfile>()

    // 1. Convert RF WiFi APs/Probes
    for (rf in rfs) {
      val dist = estimateDistance(rf.rssi, isBle = false)
      val zone = determineZone(dist)
      val isSuspect = rf.name.contains("cam", true) || rf.name.contains("spy", true)
      val tag = if (isSuspect) "Camera" else "Router/AP"
      val profile = RoomDeviceProfile(
        id = "wifi-${rf.id}",
        displayName = if (rf.name.isNotBlank()) rf.name else "[Hidden Wi-Fi SSID]",
        macOrIp = rf.id,
        vendor = if (rf.type == com.example.core.model.RfType.WIFI) "Wi-Fi AP" else "BLE Node",
        medium = "RF (${rf.frequencyMhz}MHz)",
        rssi = rf.rssi,
        distanceMeters = dist,
        zone = zone,
        confidencePercent = calculateConfidence(rf.rssi),
        isSuspectOrCamera = isSuspect,
        aiTag = tag,
        aiConfidence = if (isSuspect) 0.95f else 0.90f
      )
      profiles.add(profile)
    }

    // 2. Convert BLE Trackers and Beacons
    for (t in trackers) {
      val dist = estimateDistance(t.rssi, isBle = true)
      val zone = determineZone(dist)
      val profile = RoomDeviceProfile(
        id = "ble-${t.mac}",
        displayName = "${t.name} [${t.brand.name}]",
        macOrIp = t.mac,
        vendor = t.brand.name,
        medium = "BLE 2.4G",
        rssi = t.rssi,
        distanceMeters = dist,
        zone = zone,
        confidencePercent = calculateConfidence(t.rssi),
        isSuspectOrCamera = t.isFollowingThreat,
        aiTag = "Tracker/Beacon",
        aiConfidence = 0.96f
      )
      profiles.add(profile)
    }

    // 3. Convert Subnet & Protocol Hosts
    for (h in hosts) {
      // Subnet ping gives local network presence
      val isCam = h.isRtspCamera || 554 in h.openPorts
      val simulatedRssi = if (isCam) -48 else -62
      val dist = estimateDistance(simulatedRssi, isBle = false)
      val zone = determineZone(dist)
      val tag = if (isCam) "Camera" else if (h.vendorGuess.contains("Apple", true) || h.vendorGuess.contains("Samsung", true)) "Smartphone" else "IoT"
      val profile = RoomDeviceProfile(
        id = "host-${h.ip}",
        displayName = if (h.hostName.isNotBlank()) h.hostName else "LAN Host (${h.ip})",
        macOrIp = "${h.ip} / ${h.macAddress}",
        vendor = h.vendorGuess,
        medium = "IP/SUBNET",
        rssi = simulatedRssi,
        distanceMeters = dist,
        zone = zone,
        confidencePercent = 88,
        isSuspectOrCamera = isCam,
        aiTag = tag,
        aiConfidence = if (isCam) 0.95f else 0.85f
      )
      // Only add if not duplicate MAC
      if (profiles.none { it.macOrIp.contains(h.macAddress, ignoreCase = true) && h.macAddress.isNotBlank() }) {
        profiles.add(profile)
      }
    }

    // Deduplicate profiles by primary ID and sort by distance
    val distinctProfiles = profiles.distinctBy { it.macOrIp }.sortedBy { it.distanceMeters }
    _devices.value = distinctProfiles

    // Recompute room summary
    val immediate = distinctProfiles.count { it.zone == ProximityZone.IMMEDIATE }
    val sameRoom = distinctProfiles.count { it.zone == ProximityZone.SAME_ROOM }
    val perimeter = distinctProfiles.count { it.zone == ProximityZone.PERIMETER }
    val inRoomTotal = immediate + sameRoom
    val suspect = distinctProfiles.count { it.isSuspectOrCamera }

    val density = when {
      inRoomTotal > 12 -> "SATURATED"
      inRoomTotal > 6 -> "HIGH"
      inRoomTotal > 2 -> "MODERATE"
      else -> "LOW"
    }

    val threat = when {
      suspect > 0 -> ThreatLevel.CRITICAL
      inRoomTotal > 10 -> ThreatLevel.WARN
      else -> ThreatLevel.NOMINAL
    }

    _summary.value = RoomAuditSummary(
      totalDetectedCount = distinctProfiles.size,
      inRoomCount = inRoomTotal,
      immediateCount = immediate,
      perimeterCount = perimeter,
      suspectCount = suspect,
      estimatedRoomRfDensity = density,
      threatLevel = threat,
      lastScanTimestamp = System.currentTimeMillis()
    )

    // Save snapshot to Room database
    scope.launch {
      distinctProfiles.take(20).forEach { p ->
        repository.upsertDevice(
          macOrId = p.macOrIp,
          name = p.displayName,
          vendor = p.vendor,
          medium = p.medium,
          ipAddress = if (p.macOrIp.contains(".")) p.macOrIp.split("/").first().trim() else "",
          rssi = p.rssi,
          distanceMeters = p.distanceMeters,
          roomZone = p.zone.name,
          openPortsJson = "[]",
          threatClassification = if (p.isSuspectOrCamera) "CRITICAL" else "NOMINAL",
          riskScore = if (p.isSuspectOrCamera) 90 else 10,
          isSuspect = p.isSuspectOrCamera
        )
      }
    }
  }

  private fun recompute() {
    val current = _devices.value
    val limit = _selectedRangeLimit.value
    // Trigger StateFlow notification
    _devices.value = current.map { it }
  }

  companion object {
    /**
     * Log-Distance Path Loss Model:
     * Distance d = 10 ^ ((TxPower - RSSI) / (10 * n))
     * TxPower at 1m calibrated to:
     * -40 dBm for WiFi Access Points
     * -59 dBm for BLE Beacons
     * Path Loss Exponent n = 2.6 (Typical indoor office/room with drywall)
     */
    fun estimateDistance(rssi: Int, isBle: Boolean): Float {
      val txPower = if (isBle) -59f else -40f
      val n = 2.6f
      val ratio = (txPower - rssi.toFloat()) / (10f * n)
      val meters = 10.0.pow(ratio.toDouble()).toFloat()
      return (meters * 10f).roundToInt() / 10f
    }

    fun determineZone(distanceMeters: Float): ProximityZone {
      return when {
        distanceMeters <= ProximityZone.IMMEDIATE.maxDistanceMeters -> ProximityZone.IMMEDIATE
        distanceMeters <= ProximityZone.SAME_ROOM.maxDistanceMeters -> ProximityZone.SAME_ROOM
        distanceMeters <= ProximityZone.PERIMETER.maxDistanceMeters -> ProximityZone.PERIMETER
        else -> ProximityZone.FAR_FIELD
      }
    }

    fun calculateConfidence(rssi: Int): Int {
      return when {
        rssi >= -50 -> 98
        rssi >= -65 -> 90
        rssi >= -75 -> 80
        rssi >= -85 -> 65
        else -> 45
      }
    }
  }
}
