package com.example.core.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import com.example.core.hub.ReconHub
import com.example.core.model.RfDevice
import com.example.core.model.RfType
import com.example.core.util.RfMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AirspaceScanner(private val context: Context) {

  private val reconHub = ReconHub.getInstance()
  private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val _devices = MutableStateFlow<Map<String, RfDevice>>(emptyMap())
  val devices: StateFlow<Map<String, RfDevice>> = _devices.asStateFlow()

  private var isScanning = false

  private val wifiReceiver = object : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
      if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent?.action) {
        val results = wifiManager?.scanResults ?: return
        val current = _devices.value.toMutableMap()
        results.forEach { scan ->
          val bssid = scan.BSSID ?: return@forEach
          val ssid = if (scan.SSID.isNullOrBlank()) "<Hidden SSID>" else scan.SSID
          val freq = scan.frequency
          val ch = RfMath.frequencyToChannel(freq)
          val dist = RfMath.calculateDistance(scan.level, freq)

          current[bssid] = RfDevice(
            id = bssid,
            name = ssid,
            type = RfType.WIFI,
            rssi = scan.level,
            frequencyMhz = freq,
            channel = ch,
            distanceMeters = dist,
            timestamp = System.currentTimeMillis()
          )
        }
        _devices.value = current
        reconHub.updateRfDevices(current.values.toList())
      }
    }
  }

  private val bleCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      result ?: return
      val device = result.device ?: return
      val address = device.address ?: return
      val name = device.name ?: "BLE Emitter"
      val rssi = result.rssi
      val dist = RfMath.calculateDistance(rssi, 2400)

      val current = _devices.value.toMutableMap()
      current[address] = RfDevice(
        id = address,
        name = name,
        type = RfType.BLE,
        rssi = rssi,
        frequencyMhz = 2402,
        channel = 37,
        distanceMeters = dist,
        timestamp = System.currentTimeMillis()
      )
      _devices.value = current
      reconHub.updateRfDevices(current.values.toList())
    }
  }

  @SuppressLint("MissingPermission")
  fun startScan(scope: CoroutineScope) {
    if (isScanning) return
    isScanning = true

    try {
      val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
      context.registerReceiver(wifiReceiver, filter)
      wifiManager?.startScan()
    } catch (_: Exception) {}

    try {
      val scanner = bluetoothAdapter?.bluetoothLeScanner
      val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
      scanner?.startScan(null, settings, bleCallback)
    } catch (_: Exception) {}

    reconHub.logMessage("AIRSPACE", "Passive 2.4/5GHz and BLE scanner initiated")
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    if (!isScanning) return
    isScanning = false
    try {
      context.unregisterReceiver(wifiReceiver)
    } catch (_: Exception) {}
    try {
      bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleCallback)
    } catch (_: Exception) {}
  }
}
