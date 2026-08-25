package com.example.core.engine

import android.content.Context
import android.net.wifi.WifiManager
import com.example.core.hub.ReconHub
import com.example.core.model.ProtoService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ProtoDiscoveryEngine(private val context: Context) {

  private val reconHub = ReconHub.getInstance()
  private val _services = MutableStateFlow<List<ProtoService>>(emptyList())
  val services: StateFlow<List<ProtoService>> = _services.asStateFlow()

  private val _isDiscovering = MutableStateFlow(false)
  val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

  private var discoveryJob: Job? = null
  private var multicastLock: WifiManager.MulticastLock? = null

  fun startDiscovery(scope: CoroutineScope) {
    if (_isDiscovering.value) return
    _isDiscovering.value = true
    reconHub.logMessage("PROTO", "Multicast SSDP / ONVIF discovery listener activated")

    discoveryJob = scope.launch(Dispatchers.IO) {
      try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("red_eye_mcast")?.apply {
          setReferenceCounted(true)
          acquire()
        }

        val ssdpRequest = """
          M-SEARCH * HTTP/1.1
          HOST: 239.255.255.250:1900
          MAN: "ssdp:discover"
          MX: 3
          ST: ssdp:all
          
        """.trimIndent().replace("\n", "\r\n")

        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = 4000

        val group = InetAddress.getByName("239.255.255.250")
        val packet = DatagramPacket(ssdpRequest.toByteArray(), ssdpRequest.length, group, 1900)
        socket.send(packet)

        val buffer = ByteArray(4096)
        val incoming = DatagramPacket(buffer, buffer.size)
        val discovered = _services.value.toMutableList()

        val startTime = System.currentTimeMillis()
        while (isActive && System.currentTimeMillis() - startTime < 8000) {
          try {
            socket.receive(incoming)
            val response = String(incoming.data, 0, incoming.length)
            val host = incoming.address.hostAddress ?: "unknown"
            val port = incoming.port

            val proto = parseSsdpResponse(response, host, port)
            if (proto != null && discovered.none { it.id == proto.id }) {
              discovered.add(proto)
              _services.value = discovered.toList()
              reconHub.updateProtoServices(discovered.toList())
            }
          } catch (_: Exception) {}
        }
      } catch (e: Exception) {
        reconHub.logMessage("PROTO", "SSDP Error: ${e.message}")
      } finally {
        _isDiscovering.value = false
        try { multicastLock?.release() } catch (_: Exception) {}
      }
    }
  }

  fun stopDiscovery() {
    discoveryJob?.cancel()
    _isDiscovering.value = false
    try { multicastLock?.release() } catch (_: Exception) {}
  }

  private fun parseSsdpResponse(raw: String, host: String, port: Int): ProtoService? {
    val lines = raw.lines()
    var st = ""
    var location = ""
    var server = ""

    lines.forEach { line ->
      val lower = line.lowercase()
      if (lower.startsWith("st:")) st = line.substringAfter(":").trim()
      if (lower.startsWith("location:")) location = line.substringAfter(":").trim()
      if (lower.startsWith("server:")) server = line.substringAfter(":").trim()
    }

    var vendor = "Generic SSDP Device"
    if (server.contains("camera", true) || location.contains("onvif", true) || st.contains("onvif", true)) {
      vendor = "ONVIF IP Camera"
    } else if (server.isNotBlank()) {
      vendor = server.take(24)
    }

    val id = "$host:$port-${st.hashCode()}"
    return ProtoService(
      id = id,
      protocol = "SSDP",
      serviceType = if (st.isNotBlank()) st else "urn:schemas-upnp-org:device",
      host = host,
      port = port,
      info = if (location.isNotBlank()) location else server,
      vendorGuess = vendor,
      timestamp = System.currentTimeMillis()
    )
  }
}
