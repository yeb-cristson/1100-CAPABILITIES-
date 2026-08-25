package com.example.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.core.model.ProtoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

class ProtoEngine(private val context: Context) {

  private val scope = CoroutineScope(Dispatchers.IO)
  private var ssdpJob: Job? = null

  private val nsdManager by lazy {
    context.getSystemService(Context.NSD_SERVICE) as? NsdManager
  }

  private val _services = MutableStateFlow<List<ProtoService>>(emptyList())
  val services: StateFlow<List<ProtoService>> = _services.asStateFlow()

  private val _isDiscovering = MutableStateFlow(false)
  val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

  private val _statusMessage = MutableStateFlow("Protocol discovery standby")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private val serviceMap = mutableMapOf<String, ProtoService>()

  private val nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
    override fun onDiscoveryStarted(serviceType: String?) {
      _statusMessage.value = "mDNS & SSDP protocol sweep active"
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
      serviceInfo ?: return
      try {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
          override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

          override fun onServiceResolved(info: NsdServiceInfo?) {
            info ?: return
            val id = "mdns-${info.serviceName}-${info.host?.hostAddress}:${info.port}"
            val proto = ProtoService(
              id = id,
              protocol = "mDNS",
              serviceType = info.serviceType ?: "_http._tcp",
              host = info.host?.hostAddress ?: "unknown",
              port = info.port,
              info = info.serviceName ?: "mDNS Service",
              vendorGuess = guessVendor(info.serviceName.orEmpty(), info.serviceType.orEmpty())
            )
            synchronized(serviceMap) {
              serviceMap[id] = proto
              _services.value = serviceMap.values.toList()
            }
          }
        })
      } catch (_: Exception) {}
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
    override fun onDiscoveryStopped(serviceType: String?) {}
    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
  }

  fun startDiscovery() {
    if (_isDiscovering.value) return
    _isDiscovering.value = true

    // Start mDNS
    try {
      nsdManager?.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
    } catch (_: Exception) {}

    // Start SSDP Multicast Scanner
    ssdpJob = scope.launch {
      runSsdpMulticast()
    }
  }

  fun stopDiscovery() {
    _isDiscovering.value = false
    try {
      nsdManager?.stopServiceDiscovery(nsdDiscoveryListener)
    } catch (_: Exception) {}
    ssdpJob?.cancel()
    _statusMessage.value = "Protocol discovery standby"
  }

  fun clear() {
    synchronized(serviceMap) {
      serviceMap.clear()
      _services.value = emptyList()
    }
  }

  private suspend fun runSsdpMulticast() {
    var socket: DatagramSocket? = null
    try {
      val ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: ssdp:all\r\n\r\n"

      socket = DatagramSocket()
      socket.broadcast = true
      socket.soTimeout = 3000

      val sendData = ssdpQuery.toByteArray(Charsets.UTF_8)
      val packet = DatagramPacket(
        sendData,
        sendData.size,
        InetAddress.getByName("239.255.255.250"),
        1900
      )
      socket.send(packet)

      val receiveData = ByteArray(2048)
      val receivePacket = DatagramPacket(receiveData, receiveData.size)

      while (_isDiscovering.value) {
        try {
          socket.receive(receivePacket)
          val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
          val host = receivePacket.address.hostAddress ?: "unknown"
          val port = receivePacket.port

          val serverHeader = response.lines().firstOrNull { it.startsWith("SERVER:", true) }?.substringAfter(":")?.trim().orEmpty()
          val stHeader = response.lines().firstOrNull { it.startsWith("ST:", true) }?.substringAfter(":")?.trim().orEmpty()

          val id = "ssdp-$host:$port-${stHeader.hashCode()}"
          val proto = ProtoService(
            id = id,
            protocol = "SSDP",
            serviceType = stHeader.ifBlank { "UPnP/SSDP" },
            host = host,
            port = port,
            info = serverHeader.ifBlank { "UPnP Device" },
            vendorGuess = guessVendor(serverHeader, stHeader)
          )

          synchronized(serviceMap) {
            serviceMap[id] = proto
            _services.value = serviceMap.values.toList()
          }
        } catch (_: Exception) {}
      }
    } catch (_: Exception) {
    } finally {
      socket?.close()
    }
  }

  private fun guessVendor(name: String, type: String): String {
    val lower = "$name $type".lowercase()
    return when {
      lower.contains("onvif") -> "ONVIF Camera"
      lower.contains("dahua") -> "Dahua Camera"
      lower.contains("hikvision") -> "Hikvision Camera"
      lower.contains("axis") -> "Axis Surveillance"
      lower.contains("nest") -> "Google Nest"
      lower.contains("ring") -> "Amazon Ring"
      lower.contains("tplink") || lower.contains("kasa") || lower.contains("tapo") -> "TP-Link"
      lower.contains("sonos") -> "Sonos Audio"
      lower.contains("roku") -> "Roku Device"
      lower.contains("chromecast") || lower.contains("google") -> "Google Cast"
      else -> "UPnP / mDNS Device"
    }
  }
}
