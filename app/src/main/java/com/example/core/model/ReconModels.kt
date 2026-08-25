package com.example.core.model

enum class ThreatLevel {
  NOMINAL,
  INFO,
  WARN,
  CRITICAL
}

enum class RfType {
  BLE,
  WIFI
}

data class RfDevice(
  val id: String, // MAC or BSSID
  val name: String,
  val type: RfType,
  val rssi: Int,
  val frequencyMhz: Int = 0,
  val channel: Int = 0,
  val distanceMeters: Double = 0.0,
  val capabilities: String = "",
  val rawData: String = "",
  val firstSeenMs: Long = System.currentTimeMillis(),
  val lastSeenMs: Long = System.currentTimeMillis(),
  val timestamp: Long = System.currentTimeMillis()
)

data class SubnetHost(
  val ip: String,
  val hostName: String = "",
  val macAddress: String = "",
  val openPorts: List<Int> = emptyList(),
  val banner: String = "",
  val isRtspCamera: Boolean = false,
  val vendorGuess: String = "",
  val latencyMs: Long = 0,
  val timestamp: Long = System.currentTimeMillis()
)

data class EmReading(
  val x: Float = 0f,
  val y: Float = 0f,
  val z: Float = 0f,
  val magnitudeUt: Float = 0f,
  val baselineUt: Float = 0f,
  val deltaUt: Float = 0f,
  val isSpike: Boolean = false,
  val timestamp: Long = System.currentTimeMillis()
)

data class ProtoService(
  val id: String,
  val protocol: String, // "SSDP", "mDNS", "RTSP_BANNER"
  val serviceType: String,
  val host: String,
  val port: Int,
  val info: String,
  val vendorGuess: String = "",
  val timestamp: Long = System.currentTimeMillis()
)

data class EmitterLocation(
  val id: String,
  val name: String,
  val rssi: Int,
  val azimuthDeg: Float,
  val estimatedDistanceM: Float,
  val relX: Float, // coordinates for tactical polar radar
  val relY: Float,
  val confidence: Float = 0.8f,
  val timestamp: Long = System.currentTimeMillis()
)

data class GlintPoint(
  val x: Float, // Normalized 0..1
  val y: Float, // Normalized 0..1
  val intensity: Float = 1.0f,
  val clusterSize: Int = 1,
  val confidence: Float = 0.9f,
  val timestamp: Long = System.currentTimeMillis()
)

typealias GlintCandidate = GlintPoint

data class AcousticSpectrum(
  val peakHz: Int = 0,
  val peakMagnitude: Float = 0f,
  val ultrasonicEnergy: Float = 0f,
  val isUltrasonicActive: Boolean = false,
  val frequencyBins: FloatArray = FloatArray(64), // normalized magnitudes
  val timestamp: Long = System.currentTimeMillis()
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AcousticSpectrum) return false
    return peakHz == other.peakHz &&
      peakMagnitude == other.peakMagnitude &&
      ultrasonicEnergy == other.ultrasonicEnergy &&
      isUltrasonicActive == other.isUltrasonicActive &&
      frequencyBins.contentEquals(other.frequencyBins)
  }

  override fun hashCode(): Int {
    var result = peakHz
    result = 31 * result + peakMagnitude.hashCode()
    result = 31 * result + ultrasonicEnergy.hashCode()
    result = 31 * result + isUltrasonicActive.hashCode()
    result = 31 * result + frequencyBins.contentHashCode()
    return result
  }
}

enum class TrackerBrand {
  APPLE_AIRTAG,
  SAMSUNG_SMARTTAG,
  TILE,
  IBEACON,
  EDDYSTONE,
  GENERIC_BEACON
}

data class BleTracker(
  val mac: String,
  val name: String,
  val brand: TrackerBrand,
  val rssi: Int,
  val sightingCount: Int,
  val firstSeenMs: Long,
  val lastSeenMs: Long,
  val isFollowingThreat: Boolean = false,
  val manufacturerHex: String = ""
)

data class ThreatAlert(
  val id: String,
  val level: ThreatLevel,
  val title: String,
  val details: String,
  val sourceModule: String,
  val timestamp: Long = System.currentTimeMillis()
)

data class FusionState(
  val threatLevel: ThreatLevel = ThreatLevel.NOMINAL,
  val activeAlerts: List<ThreatAlert> = emptyList(),
  val rfDeviceCount: Int = 0,
  val bleTrackerCount: Int = 0,
  val subnetHostCount: Int = 0,
  val rtspCameraCount: Int = 0,
  val emMagnitudeUt: Float = 0f,
  val emSpikeActive: Boolean = false,
  val glintCount: Int = 0,
  val ultrasonicActive: Boolean = false,
  val acousticPeakHz: Int = 0,
  val lastUpdateMs: Long = System.currentTimeMillis()
)

data class UserSpatialCoordinate(
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val altitudeM: Double = 0.0,
  val accuracyM: Float = 5.0f,
  val headingDegrees: Float = 0.0f,
  val provider: String = "GPS_FUSION",
  val timestamp: Long = System.currentTimeMillis()
)

data class SpatialDevicePoint(
  val id: String,
  val name: String,
  val identifier: String,
  val category: com.example.core.util.DeviceCategory,
  val categoryLabel: String,
  val vendor: String,
  val rssi: Int,
  val distanceMeters: Double,
  val bearingDeg: Float,
  val relX: Float, // Relative X (meters East)
  val relY: Float, // Relative Y (meters North)
  val latitude: Double,
  val longitude: Double,
  val densityWeight: Float, // 0.0 to 1.0 based on signal strength
  val threatScore: Int,
  val isOfflineOrUnassociated: Boolean = true,
  val lastSeenMs: Long = System.currentTimeMillis()
)

data class HeatmapDensityCell(
  val x: Float,
  val y: Float,
  val density: Float,
  val dominantCategory: com.example.core.util.DeviceCategory
)

data class SpatialHeatmapState(
  val userLocation: UserSpatialCoordinate = UserSpatialCoordinate(),
  val spatialDevices: List<SpatialDevicePoint> = emptyList(),
  val totalDeviceDensity: Float = 0f,
  val peakClusterCount: Int = 0,
  val coverageRadiusMeters: Float = 30f,
  val selectedFilterCategory: com.example.core.util.DeviceCategory? = null,
  val isRealTimeTracking: Boolean = true
)

