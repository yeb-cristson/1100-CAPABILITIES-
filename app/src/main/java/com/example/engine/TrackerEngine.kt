package com.example.engine

import com.example.core.model.BleTracker
import com.example.core.model.RfDevice
import com.example.core.model.RfType
import com.example.core.model.TrackerBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackerEngine {

  private val _trackers = MutableStateFlow<List<BleTracker>>(emptyList())
  val trackers: StateFlow<List<BleTracker>> = _trackers.asStateFlow()

  private val _followingThreats = MutableStateFlow<List<BleTracker>>(emptyList())
  val followingThreats: StateFlow<List<BleTracker>> = _followingThreats.asStateFlow()

  private val trackerMap = mutableMapOf<String, BleTracker>()

  fun processRfDevices(devices: List<RfDevice>) {
    val now = System.currentTimeMillis()
    devices.filter { it.type == RfType.BLE }.forEach { dev ->
      val brand = identifyTrackerBrand(dev.name, dev.rawData)
      if (brand != null) {
        val existing = trackerMap[dev.id]
        val sightings = (existing?.sightingCount ?: 0) + 1
        val firstSeen = existing?.firstSeenMs ?: now
        val timeSpanMs = now - firstSeen

        // Stalker heuristic: Seen >= 4 times over > 90 seconds
        val isFollowing = sightings >= 4 && timeSpanMs > 90_000

        val tracker = BleTracker(
          mac = dev.id,
          name = if (dev.name != "BLE Emitter") dev.name else formatBrandName(brand),
          brand = brand,
          rssi = dev.rssi,
          sightingCount = sightings,
          firstSeenMs = firstSeen,
          lastSeenMs = now,
          isFollowingThreat = isFollowing,
          manufacturerHex = dev.rawData
        )
        trackerMap[dev.id] = tracker
      }
    }

    val allTrackers = trackerMap.values.sortedByDescending { it.rssi }
    _trackers.value = allTrackers
    _followingThreats.value = allTrackers.filter { it.isFollowingThreat }
  }

  fun clear() {
    trackerMap.clear()
    _trackers.value = emptyList()
    _followingThreats.value = emptyList()
  }

  private fun identifyTrackerBrand(name: String, rawHex: String): TrackerBrand? {
    val n = name.lowercase()
    val hex = rawHex.uppercase()

    return when {
      n.contains("airtag") || hex.contains("4C001219") || hex.contains("4C0007") -> TrackerBrand.APPLE_AIRTAG
      n.contains("smarttag") || hex.contains("0075") || hex.contains("FD5A") -> TrackerBrand.SAMSUNG_SMARTTAG
      n.contains("tile") || hex.contains("FEED") -> TrackerBrand.TILE
      hex.contains("4C000215") -> TrackerBrand.IBEACON
      hex.contains("FEAA") -> TrackerBrand.EDDYSTONE
      n.contains("beacon") || n.contains("tag") || n.contains("tracker") -> TrackerBrand.GENERIC_BEACON
      else -> null
    }
  }

  private fun formatBrandName(brand: TrackerBrand): String {
    return when (brand) {
      TrackerBrand.APPLE_AIRTAG -> "Apple AirTag (Find My)"
      TrackerBrand.SAMSUNG_SMARTTAG -> "Samsung Galaxy SmartTag"
      TrackerBrand.TILE -> "Tile Bluetooth Tracker"
      TrackerBrand.IBEACON -> "Apple iBeacon"
      TrackerBrand.EDDYSTONE -> "Google Eddystone Beacon"
      TrackerBrand.GENERIC_BEACON -> "Personal BLE Beacon"
    }
  }
}
