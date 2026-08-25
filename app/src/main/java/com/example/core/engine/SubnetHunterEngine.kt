package com.example.core.engine

import com.example.core.hub.ReconHub
import com.example.core.model.SubnetHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class SubnetHunterEngine {

  private val reconHub = ReconHub.getInstance()
  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _progress = MutableStateFlow(0f)
  val progress: StateFlow<Float> = _progress.asStateFlow()

  private val _scannedCount = MutableStateFlow(0)
  val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

  private val _currentSubnet = MutableStateFlow("192.168.1.0/24")
  val currentSubnet: StateFlow<String> = _currentSubnet.asStateFlow()

  private var sweepJob: Job? = null
  private val probePorts = listOf(80, 554, 8080, 8000, 8888, 1935, 443, 22)

  fun startSubnetSweep(scope: CoroutineScope) {
    if (_isScanning.value) return
    sweepJob?.cancel()

    sweepJob = scope.launch(Dispatchers.IO) {
      _isScanning.value = true
      _progress.value = 0f
      _scannedCount.value = 0

      val baseIp = getLocalSubnetPrefix()
      _currentSubnet.value = "$baseIp.0/24"
      reconHub.logMessage("SUBNET", "Initiating 64-way async /24 sweep on $baseIp.0/24")

      val discoveredHosts = mutableListOf<SubnetHost>()
      val semaphore = kotlinx.coroutines.sync.Semaphore(64)
      var processed = 0

      val jobs = (1..254).map { hostNum ->
        val ip = "$baseIp.$hostNum"
        launch {
          semaphore.acquire()
          try {
            val host = probeHost(ip)
            if (host != null) {
              synchronized(discoveredHosts) {
                discoveredHosts.add(host)
                reconHub.updateSubnetHosts(discoveredHosts.toList())
              }
            }
          } finally {
            semaphore.release()
            synchronized(this@SubnetHunterEngine) {
              processed++
              _scannedCount.value = processed
              _progress.value = processed / 254f
            }
          }
        }
      }

      jobs.joinAll()
      _isScanning.value = false
      reconHub.logMessage("SUBNET", "Sweep finished: ${discoveredHosts.size} active hosts responding")
    }
  }

  fun cancelSweep() {
    sweepJob?.cancel()
    _isScanning.value = false
    reconHub.logMessage("SUBNET", "Subnet sweep aborted by operator")
  }

  private fun probeHost(ip: String): SubnetHost? {
    var isAlive = false
    var latency = 0L
    val openPorts = mutableListOf<Int>()
    var banner = ""
    var isRtsp = false
    var vendor = "Unknown Device"

    val start = System.currentTimeMillis()
    try {
      val addr = InetAddress.getByName(ip)
      if (addr.isReachable(120)) {
        isAlive = true
        latency = System.currentTimeMillis() - start
      }
    } catch (_: Exception) {}

    for (port in probePorts) {
      try {
        Socket().use { sock ->
          sock.connect(InetSocketAddress(ip, port), 90)
          openPorts.add(port)
          isAlive = true

          if (port == 554) {
            isRtsp = true
            vendor = "RTSP Camera / IP Surveillance"
          } else if (port == 80 || port == 8080) {
            if (vendor == "Unknown Device") vendor = "Web Device / IoT"
          }
        }
      } catch (_: Exception) {}
    }

    if (!isAlive && openPorts.isEmpty()) return null

    return SubnetHost(
      ip = ip,
      openPorts = openPorts,
      banner = banner,
      isRtspCamera = isRtsp,
      vendorGuess = vendor,
      latencyMs = if (latency > 0) latency else 15L,
      timestamp = System.currentTimeMillis()
    )
  }

  private fun getLocalSubnetPrefix(): String {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val intf = interfaces.nextElement()
        if (intf.isLoopback || !intf.isUp) continue
        val addrs = intf.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
            val host = addr.hostAddress ?: continue
            val parts = host.split(".")
            if (parts.size == 4) {
              return "${parts[0]}.${parts[1]}.${parts[2]}"
            }
          }
        }
      }
    } catch (_: Exception) {}
    return "192.168.1"
  }
}
