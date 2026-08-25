package com.example.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import com.example.core.model.RfDevice
import com.example.core.model.RfType
import com.example.core.util.RfMath
import com.example.core.util.SurveillanceClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AirspaceEngine(private val context: Context) {

  private val scope = CoroutineScope(Dispatchers.Default)
  private var loopJob: Job? = null

  private val bluetoothManager by lazy {
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  }
  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    bluetoothManager?.adapter
  }
  private val wifiManager by lazy {
    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  }
  private val wifiP2pManager by lazy {
    context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
  }
  private var p2pChannel: WifiP2pManager.Channel? = null

  private val _devices = MutableStateFlow<List<RfDevice>>(emptyList())
  val devices: StateFlow<List<RfDevice>> = _devices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _statusMessage = MutableStateFlow("Airspace monitor standby")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private val deviceMap = mutableMapOf<String, RfDevice>()

  private val bleCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      result ?: return
      processBleResult(result)
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
      results?.forEach { processBleResult(it) }
    }

    override fun onScanFailed(errorCode: Int) {
      _statusMessage.value = "BLE scan failed with code: $errorCode"
    }
  }

  private val radioReceiver = object : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(c: Context?, intent: Intent?) {
      when (intent?.action) {
        WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
          val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
          if (success) {
            wifiManager?.scanResults?.let { list ->
              processWifiResults(list)
            }
          }
        }
        BluetoothDevice.ACTION_FOUND -> {
          val dev: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
          if (dev != null && rssi > -120) {
            processClassicBtResult(dev, rssi)
          }
        }
        WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
          wifiP2pManager?.requestPeers(p2pChannel) { peers: WifiP2pDeviceList? ->
            peers?.deviceList?.forEach { p2pDev ->
              processP2pDevice(p2pDev)
            }
          }
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun startScan() {
    if (_isScanning.value) return
    _isScanning.value = true
    _statusMessage.value = "Active RF & unassociated airspace monitoring engaged"

    // 1. Start Aggressive Low Latency BLE Scan
    try {
      val scanner = bluetoothAdapter?.bluetoothLeScanner
      if (scanner != null && bluetoothAdapter?.isEnabled == true) {
        val settings = ScanSettings.Builder()
          .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
          .setReportDelay(0)
          .build()
        scanner.startScan(null, settings, bleCallback)
      } else {
        _statusMessage.value = "Bluetooth inactive or unavailable"
      }
    } catch (e: Exception) {
      _statusMessage.value = "BLE init error: ${e.localizedMessage}"
    }

    // 2. Start Bluetooth Classic Discovery for unassociated laptops/phones/audio nodes
    try {
      if (bluetoothAdapter?.isEnabled == true) {
        bluetoothAdapter?.startDiscovery()
      }
    } catch (_: Exception) {}

    // 3. Register Multi-Radio Receiver (Wi-Fi, Bluetooth Classic, Wi-Fi Direct)
    try {
      val filter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
      }
      context.registerReceiver(radioReceiver, filter)
      wifiManager?.startScan()

      // Initialize Wi-Fi Direct P2P discovery
      p2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
      wifiP2pManager?.discoverPeers(p2pChannel, null)
    } catch (e: Exception) {
      _statusMessage.value = "Radio scan registration error: ${e.localizedMessage}"
    }

    // 4. Periodic polling loop for continuous active sweep
    loopJob = scope.launch {
      while (isActive && _isScanning.value) {
        delay(6000)
        try {
          wifiManager?.startScan()
          if (bluetoothAdapter?.isDiscovering == false) {
            bluetoothAdapter?.startDiscovery()
          }
          wifiP2pManager?.discoverPeers(p2pChannel, null)
        } catch (_: Exception) {}
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    _isScanning.value = false
    loopJob?.cancel()
    loopJob = null
    try {
      bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleCallback)
      bluetoothAdapter?.cancelDiscovery()
    } catch (_: Exception) {}
    try {
      context.unregisterReceiver(radioReceiver)
    } catch (_: Exception) {}
    _statusMessage.value = "Airspace monitor standby"
  }

  fun clear() {
    deviceMap.clear()
    _devices.value = emptyList()
  }

  @SuppressLint("MissingPermission")
  private fun processBleResult(result: ScanResult) {
    scope.launch {
      val dev = result.device ?: return@launch
      val mac = dev.address ?: return@launch
      val recordBytes = result.scanRecord?.bytes
      val hex = if (recordBytes != null) RfMath.bytesToHex(recordBytes.take(28).toByteArray()) else ""

      val name = dev.name ?: result.scanRecord?.deviceName ?: run {
        val classified = SurveillanceClassifier.classifyDevice(mac, "", "BLE", hex, true)
        classified.categoryLabel
      }
      val rssi = result.rssi
      val distance = RfMath.calculateDistance(rssi, -59, 2.7)

      val existing = deviceMap[mac]
      val firstSeen = existing?.firstSeenMs ?: System.currentTimeMillis()

      val rfDevice = RfDevice(
        id = mac,
        name = name,
        type = RfType.BLE,
        rssi = rssi,
        frequencyMhz = 2402,
        channel = 37,
        distanceMeters = distance,
        capabilities = "ADV_IND / BLE",
        rawData = hex,
        firstSeenMs = firstSeen,
        lastSeenMs = System.currentTimeMillis()
      )

      deviceMap[mac] = rfDevice
      _devices.value = deviceMap.values.sortedByDescending { it.rssi }
    }
  }

  @SuppressLint("MissingPermission")
  private fun processClassicBtResult(dev: BluetoothDevice, rssi: Int) {
    scope.launch {
      val mac = dev.address ?: return@launch
      val name = dev.name ?: "Bluetooth Device (${mac.takeLast(5)})"
      val distance = RfMath.calculateDistance(rssi, -55, 2.8)

      val existing = deviceMap[mac]
      val firstSeen = existing?.firstSeenMs ?: System.currentTimeMillis()

      val rfDevice = RfDevice(
        id = mac,
        name = name,
        type = RfType.BLE,
        rssi = rssi,
        frequencyMhz = 2440,
        channel = 40,
        distanceMeters = distance,
        capabilities = "BT_CLASSIC / INQUIRY_RESP",
        rawData = "CLASSIC_BT_DEV",
        firstSeenMs = firstSeen,
        lastSeenMs = System.currentTimeMillis()
      )
      deviceMap[mac] = rfDevice
      _devices.value = deviceMap.values.sortedByDescending { it.rssi }
    }
  }

  private fun processP2pDevice(p2pDev: WifiP2pDevice) {
    scope.launch {
      val mac = p2pDev.deviceAddress ?: return@launch
      val name = if (p2pDev.deviceName.isNotBlank()) "P2P: ${p2pDev.deviceName}" else "Wi-Fi Direct Peer"
      val existing = deviceMap[mac]
      val firstSeen = existing?.firstSeenMs ?: System.currentTimeMillis()

      val rfDevice = RfDevice(
        id = mac,
        name = name,
        type = RfType.WIFI,
        rssi = -50,
        frequencyMhz = 2412,
        channel = 1,
        distanceMeters = 2.5,
        capabilities = "WIFI_DIRECT / P2P_PEER",
        rawData = "P2P_STATUS_${p2pDev.status}",
        firstSeenMs = firstSeen,
        lastSeenMs = System.currentTimeMillis()
      )
      deviceMap[mac] = rfDevice
      _devices.value = deviceMap.values.sortedByDescending { it.rssi }
    }
  }

  private fun processWifiResults(results: List<WifiScanResult>) {
    scope.launch {
      results.forEach { res ->
        val bssid = res.BSSID ?: return@forEach
        val ssid = if (res.SSID.isNullOrBlank()) "<Hidden SSID>" else res.SSID
        val rssi = res.level
        val freq = res.frequency
        val channel = RfMath.wifiFrequencyToChannel(freq)
        val distance = RfMath.calculateDistance(rssi, -45, 3.0)

        val existing = deviceMap[bssid]
        val firstSeen = existing?.firstSeenMs ?: System.currentTimeMillis()

        val rfDevice = RfDevice(
          id = bssid,
          name = ssid,
          type = RfType.WIFI,
          rssi = rssi,
          frequencyMhz = freq,
          channel = channel,
          distanceMeters = distance,
          capabilities = res.capabilities ?: "",
          rawData = res.capabilities ?: "",
          firstSeenMs = firstSeen,
          lastSeenMs = System.currentTimeMillis()
        )
        deviceMap[bssid] = rfDevice
      }
      _devices.value = deviceMap.values.sortedByDescending { it.rssi }
    }
  }
}

