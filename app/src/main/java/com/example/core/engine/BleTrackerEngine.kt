package com.example.core.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.example.core.hub.ReconHub
import com.example.core.model.BleTracker
import com.example.core.model.TrackerBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleTrackerEngine(private val context: Context) {

  private val reconHub = ReconHub.getInstance()
  private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val _trackers = MutableStateFlow<Map<String, BleTracker>>(emptyMap())
  val trackers: StateFlow<Map<String, BleTracker>> = _trackers.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val bleCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      result ?: return
      val device = result.device ?: return
      val mac = device.address ?: return
      val rssi = result.rssi
      val scanRecord = result.scanRecord ?: return

      val brand = identifyBrand(scanRecord) ?: return
      val now = System.currentTimeMillis()

      val current = _trackers.value.toMutableMap()
      val existing = current[mac]

      val sightings = (existing?.sightingCount ?: 0) + 1
      val firstSeen = existing?.firstSeenMs ?: now
      val elapsedSec = (now - firstSeen) / 1000

      val isThreat = sightings >= 5 && elapsedSec > 15

      val tracker = BleTracker(
        mac = mac,
        name = device.name ?: brand.name,
        brand = brand,
        rssi = rssi,
        sightingCount = sightings,
        firstSeenMs = firstSeen,
        lastSeenMs = now,
        isFollowingThreat = isThreat
      )

      current[mac] = tracker
      _trackers.value = current
      reconHub.updateBleTrackers(current.values.toList())
    }
  }

  private fun identifyBrand(record: android.bluetooth.le.ScanRecord): TrackerBrand? {
    val mfgData = record.manufacturerSpecificData
    if (mfgData != null) {
      for (i in 0 until mfgData.size()) {
        val id = mfgData.keyAt(i)
        val data = mfgData.valueAt(i)
        if (id == 0x004C) {
          if (data != null && data.isNotEmpty()) {
            if (data[0].toInt() == 0x12) return TrackerBrand.APPLE_AIRTAG
            if (data[0].toInt() == 0x02) return TrackerBrand.IBEACON
          }
          return TrackerBrand.APPLE_AIRTAG
        }
        if (id == 0x0075) return TrackerBrand.SAMSUNG_SMARTTAG
        if (id == 0x00D0) return TrackerBrand.TILE
      }
    }

    val serviceUuids = record.serviceUuids
    if (serviceUuids != null) {
      for (uuid in serviceUuids) {
        val str = uuid.uuid.toString().lowercase()
        if (str.contains("feed")) return TrackerBrand.TILE
        if (str.contains("fd6f") || str.contains("fe9f")) return TrackerBrand.GENERIC_BEACON
      }
    }

    return null
  }

  @SuppressLint("MissingPermission")
  fun start() {
    if (_isScanning.value) return
    _isScanning.value = true
    try {
      val scanner = bluetoothAdapter?.bluetoothLeScanner
      val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
      scanner?.startScan(null, settings, bleCallback)
      reconHub.logMessage("TRACKER", "Stalker tracker detection pipeline active")
    } catch (e: Exception) {
      reconHub.logMessage("TRACKER", "BLE Init error: ${e.message}")
    }
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    if (!_isScanning.value) return
    _isScanning.value = false
    try {
      bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleCallback)
    } catch (_: Exception) {}
  }
}
