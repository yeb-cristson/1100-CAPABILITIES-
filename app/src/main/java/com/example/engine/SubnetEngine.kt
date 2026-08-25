package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.example.core.model.SubnetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class SubnetEngine(private val context: Context) {

  private val scope = CoroutineScope(Dispatchers.IO)
  private var scanJob: Job? = null

  private val _hosts = MutableStateFlow<List<SubnetHost>>(emptyList())
  val hosts: StateFlow<List<SubnetHost>> = _hosts.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _scanProgress = MutableStateFlow(0f)
  val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

  private val _currentSubnet = MutableStateFlow<String?>("Local / Offline")
  val currentSubnet: StateFlow<String?> = _currentSubnet.asStateFlow()

  private val _statusMessage = MutableStateFlow("Subnet hunter standby")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private val targetPorts = listOf(554, 80, 443, 8000, 8080, 8554, 1935)

  fun startScan() {
    if (_isScanning.value) return
    _isScanning.value = true
    _scanProgress.value = 0f
    _statusMessage.value = "Resolving local network subnet..."

    scanJob = scope.launch {
      val localIp = getLocalIpAddress()
      if (localIp == null) {
        _statusMessage.value = "No active Wi-Fi / LAN interface connected"
        _currentSubnet.value = "Offline Mode"
        _isScanning.value = false
        return@launch
      }

      val prefix = localIp.substringBeforeLast(".")
      _currentSubnet.value = "$prefix.0/24"
      _statusMessage.value = "Sweeping $prefix.1 - $prefix.254 for hosts & RTSP (554)..."

      val discovered = mutableListOf<SubnetHost>()

      val chunkSize = 16
      for (chunkStart in 1..254 step chunkSize) {
        val chunkEnd = (chunkStart + chunkSize - 1).coerceAtMost(254)
        val jobs = (chunkStart..chunkEnd).map { i ->
          launch {
            val ip = "$prefix.$i"
            val host = probeHost(ip)
            if (host != null) {
              synchronized(discovered) {
                discovered.add(host)
                _hosts.value = discovered.sortedBy { it.ip }
              }
            }
          }
        }
        jobs.forEach { it.join() }
        _scanProgress.value = chunkEnd / 254f
      }

      _statusMessage.value = "Subnet sweep completed. Found ${discovered.size} active hosts."
      _isScanning.value = false
    }
  }

  fun stopScan() {
    _isScanning.value = false
    scanJob?.cancel()
    _statusMessage.value = "Subnet scan aborted"
  }

  fun clear() {
    _hosts.value = emptyList()
    _scanProgress.value = 0f
  }

  private suspend fun probeHost(ip: String): SubnetHost? = withContext(Dispatchers.IO) {
    try {
      val addr = InetAddress.getByName(ip)
      val startTime = System.currentTimeMillis()
      val isReachable = addr.isReachable(300)
      val latency = System.currentTimeMillis() - startTime

      val openPorts = mutableListOf<Int>()
      var isRtsp = false
      var banner = ""

      // Test RTSP 554 first
      if (checkPort(ip, 554, 250)) {
        openPorts.add(554)
        isRtsp = true
        banner = "RTSP Video Server Active"
      }

      // If reachable or 554 was open, test remaining common ports
      if (isReachable || isRtsp) {
        for (port in targetPorts) {
          if (port != 554 && checkPort(ip, port, 150)) {
            openPorts.add(port)
            if (port == 8554 || port == 8000) isRtsp = true
          }
        }

        val vendorGuess = when {
          isRtsp -> "IP Surveillance Camera"
          openPorts.contains(80) || openPorts.contains(443) -> "Web Server / Gateway"
          else -> "LAN Host"
        }

        SubnetHost(
          ip = ip,
          hostName = addr.hostName ?: ip,
          macAddress = "",
          openPorts = openPorts,
          banner = banner,
          isRtspCamera = isRtsp,
          vendorGuess = vendorGuess,
          latencyMs = latency
        )
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun checkPort(ip: String, port: Int, timeoutMs: Int): Boolean {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        true
      }
    } catch (_: Exception) {
      false
    }
  }

  private fun getLocalIpAddress(): String? {
    try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
      if (ipInt != 0) {
        return "%d.%d.%d.%d".format(
          ipInt and 0xff,
          ipInt shr 8 and 0xff,
          ipInt shr 16 and 0xff,
          ipInt shr 24 and 0xff
        )
      }
    } catch (_: Exception) {}
    return null
  }
}
