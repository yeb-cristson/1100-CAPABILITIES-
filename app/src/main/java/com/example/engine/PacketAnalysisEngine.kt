package com.example.engine

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

data class CapturedPacket(
  val id: String = UUID.randomUUID().toString().take(8),
  val timestamp: Long = System.currentTimeMillis(),
  val formattedTime: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
  val protocol: String, // TCP, UDP, ICMP, mDNS, SSDP, RTSP, DNS, DHCP, HTTP, ARP
  val sourceIp: String,
  val destIp: String,
  val sourcePort: Int = 0,
  val destPort: Int = 0,
  val packetLength: Int,
  val ttl: Int = 64,
  val osFingerprint: String = "Linux / Android / POSIX",
  val tcpFlags: String = "",
  val payloadSummary: String = "",
  val hexDump: String = "",
  val isThreatAnomaly: Boolean = false,
  val threatDetail: String = ""
)

data class HostTrafficFootprint(
  val ip: String,
  val mac: String = "Unknown",
  val hostName: String = "",
  val osGuess: String = "Generic Host",
  val totalPackets: Int = 0,
  val totalBytes: Long = 0L,
  val activePorts: List<Int> = emptyList(),
  val protocolsSeen: List<String> = emptyList(),
  val lastSeen: Long = System.currentTimeMillis(),
  val isSuspicious: Boolean = false,
  val reason: String = ""
)

class PacketAnalysisEngine(private val context: Context) {

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var captureJob: Job? = null

  private val _isCapturing = MutableStateFlow(false)
  val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

  private val _packets = MutableStateFlow<List<CapturedPacket>>(emptyList())
  val packets: StateFlow<List<CapturedPacket>> = _packets.asStateFlow()

  private val _hostFootprints = MutableStateFlow<Map<String, HostTrafficFootprint>>(emptyMap())
  val hostFootprints: StateFlow<Map<String, HostTrafficFootprint>> = _hostFootprints.asStateFlow()

  private val _totalPacketsCaptured = MutableStateFlow(0L)
  val totalPacketsCaptured: StateFlow<Long> = _totalPacketsCaptured.asStateFlow()

  private val _totalBytesCaptured = MutableStateFlow(0L)
  val totalBytesCaptured: StateFlow<Long> = _totalBytesCaptured.asStateFlow()

  private val _promiscuousStatus = MutableStateFlow("STANDARD RAW SOCKET CAPTURE")
  val promiscuousStatus: StateFlow<String> = _promiscuousStatus.asStateFlow()

  private val openSockets = Collections.synchronizedList(mutableListOf<DatagramSocket>())
  private val hostMap = ConcurrentHashMap<String, HostTrafficFootprint>()

  private var wifiMulticastLock: WifiManager.MulticastLock? = null

  fun startCapture() {
    if (_isCapturing.value) return
    _isCapturing.value = true

    // Acquire Multicast Lock to receive broadcast/multicast packets on Wi-Fi
    try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      wifiMulticastLock = wifiManager?.createMulticastLock("AegisPacketLock")?.apply {
        setReferenceCounted(true)
        acquire()
      }
    } catch (e: Exception) {
      Log.w("PacketEngine", "Could not acquire MulticastLock: ${e.message}")
    }

    captureJob = scope.launch {
      // 1. Launch socket listeners on network discovery & broadcast ports
      launchMulticastListener("239.255.255.250", 1900, "SSDP")
      launchMulticastListener("224.0.0.251", 5353, "mDNS")
      launchBroadcastListener(137, "NetBIOS")
      launchBroadcastListener(67, "DHCP")
      launchBroadcastListener(8888, "AEGIS_BROADCAST")

      // 2. Active kernel ARP & TCP socket table poll loop
      launch {
        while (isActive && _isCapturing.value) {
          inspectKernelArpTable()
          inspectKernelSocketTable()
          delay(2000)
        }
      }
    }
  }

  fun stopCapture() {
    _isCapturing.value = false
    captureJob?.cancel()
    captureJob = null

    synchronized(openSockets) {
      openSockets.forEach {
        runCatching { it.close() }
      }
      openSockets.clear()
    }

    runCatching {
      wifiMulticastLock?.release()
      wifiMulticastLock = null
    }
  }

  fun clearPackets() {
    _packets.value = emptyList()
    _totalPacketsCaptured.value = 0L
    _totalBytesCaptured.value = 0L
    hostMap.clear()
    _hostFootprints.value = emptyMap()
  }

  private fun launchMulticastListener(groupAddr: String, port: Int, protoLabel: String) {
    scope.launch {
      var mSocket: MulticastSocket? = null
      try {
        mSocket = MulticastSocket(port).apply {
          reuseAddress = true
          soTimeout = 3000
          val group = InetAddress.getByName(groupAddr)
          joinGroup(group)
        }
        synchronized(openSockets) { openSockets.add(mSocket) }

        val buffer = ByteArray(4096)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isActive && _isCapturing.value) {
          try {
            mSocket.receive(packet)
            val length = packet.length
            val srcIp = packet.address.hostAddress ?: "Unknown"
            val srcPort = packet.port
            val rawData = buffer.copyOf(length)

            processCapturedPacket(
              proto = protoLabel,
              srcIp = srcIp,
              dstIp = groupAddr,
              srcPort = srcPort,
              dstPort = port,
              length = length,
              data = rawData
            )
          } catch (e: SocketTimeoutException) {
            // normal timeout to check isActive
          } catch (e: Exception) {
            if (!_isCapturing.value) break
            delay(1000)
          }
        }
      } catch (e: Exception) {
        Log.w("PacketEngine", "Multicast listener error on $port ($protoLabel): ${e.message}")
      } finally {
        runCatching { mSocket?.close() }
      }
    }
  }

  private fun launchBroadcastListener(port: Int, protoLabel: String) {
    scope.launch {
      var dSocket: DatagramSocket? = null
      try {
        dSocket = DatagramSocket(null).apply {
          reuseAddress = true
          bind(InetSocketAddress(port))
          soTimeout = 3000
        }
        synchronized(openSockets) { openSockets.add(dSocket) }

        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isActive && _isCapturing.value) {
          try {
            dSocket.receive(packet)
            val length = packet.length
            val srcIp = packet.address.hostAddress ?: "Unknown"
            val srcPort = packet.port
            val rawData = buffer.copyOf(length)

            processCapturedPacket(
              proto = protoLabel,
              srcIp = srcIp,
              dstIp = "255.255.255.255",
              srcPort = srcPort,
              dstPort = port,
              length = length,
              data = rawData
            )
          } catch (e: SocketTimeoutException) {
            // normal
          } catch (e: Exception) {
            if (!_isCapturing.value) break
            delay(1000)
          }
        }
      } catch (e: Exception) {
        Log.w("PacketEngine", "Broadcast listener error on $port: ${e.message}")
      } finally {
        runCatching { dSocket?.close() }
      }
    }
  }

  /**
   * Reads the active Linux Kernel ARP table at /proc/net/arp to discover local traffic nodes.
   */
  private fun inspectKernelArpTable() {
    try {
      val arp = File("/proc/net/arp")
      if (arp.exists() && arp.canRead()) {
        val lines = arp.readLines()
        lines.drop(1).forEach { line ->
          val parts = line.split("\\s+".toRegex())
          if (parts.size >= 4) {
            val ip = parts[0]
            val mac = parts[3]
            val dev = if (parts.size >= 6) parts[5] else "wlan0"

            if (mac != "00:00:00:00:00:00" && ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
              val existing = hostMap[ip]
              val updated = (existing ?: HostTrafficFootprint(ip = ip)).copy(
                ip = ip,
                mac = mac,
                lastSeen = System.currentTimeMillis()
              )
              hostMap[ip] = updated
            }
          }
        }
        _hostFootprints.value = hostMap.toMap()
      }
    } catch (e: Exception) {
      Log.d("PacketEngine", "ARP parse note: ${e.message}")
    }
  }

  /**
   * Reads active Linux Kernel TCP/UDP connections table at /proc/net/tcp.
   */
  private fun inspectKernelSocketTable() {
    try {
      val tcpFile = File("/proc/net/tcp")
      if (tcpFile.exists() && tcpFile.canRead()) {
        val lines = tcpFile.readLines()
        lines.drop(1).forEach { line ->
          val parts = line.trim().split("\\s+".toRegex())
          if (parts.size >= 4) {
            val remoteHex = parts[2]
            val remoteParts = remoteHex.split(":")
            if (remoteParts.size == 2) {
              val ipHex = remoteParts[0]
              val portHex = remoteParts[1]
              val port = portHex.toIntOrNull(16) ?: 0
              if (ipHex.length == 8) {
                val b1 = ipHex.substring(6, 8).toInt(16)
                val b2 = ipHex.substring(4, 6).toInt(16)
                val b3 = ipHex.substring(2, 4).toInt(16)
                val b4 = ipHex.substring(0, 2).toInt(16)
                val ip = "$b1.$b2.$b3.$b4"
                if (ip != "0.0.0.0" && port > 0) {
                  updateHostActivity(ip, "TCP", port, 64)
                }
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.d("PacketEngine", "Socket table parse note: ${e.message}")
    }
  }

  fun processCapturedPacket(
    proto: String,
    srcIp: String,
    dstIp: String,
    srcPort: Int,
    dstPort: Int,
    length: Int,
    data: ByteArray
  ) {
    val hexStr = data.take(48).joinToString(" ") { "%02X".format(it) }
    val asciiStr = String(data.take(min(length, 120)).toByteArray(), Charsets.US_ASCII)
      .replace(Regex("[^\\x20-\\x7E]"), ".")

    // Determine OS fingerprint heuristic from protocol/payload or standard TTL
    val (ttl, osFingerprint) = when {
      proto == "SSDP" && asciiStr.contains("Windows", true) -> Pair(128, "Microsoft Windows (NT Core)")
      proto == "mDNS" && (asciiStr.contains("apple", true) || asciiStr.contains("airplay", true)) -> Pair(64, "Apple Darwin (iOS/macOS)")
      asciiStr.contains("Android", true) || asciiStr.contains("Linux", true) -> Pair(64, "Linux / Android Kernel")
      proto == "RTSP" -> Pair(64, "Embedded Linux (IP Camera)")
      else -> Pair(64, "POSIX / Embedded Device")
    }

    val isRtsp = dstPort == 554 || srcPort == 554 || asciiStr.contains("RTSP", true)
    val isThreat = isRtsp || asciiStr.contains("CAMERA", true) || asciiStr.contains("STREAM", true)
    val threatDetail = if (isRtsp) "Active RTSP Video Streaming Protocol on Port $dstPort" else if (isThreat) "Suspicious Covert Surveillance Payload Signature" else ""

    val captured = CapturedPacket(
      protocol = if (isRtsp) "RTSP" else proto,
      sourceIp = srcIp,
      destIp = dstIp,
      sourcePort = srcPort,
      destPort = dstPort,
      packetLength = length,
      ttl = ttl,
      osFingerprint = osFingerprint,
      tcpFlags = if (proto == "TCP") "ACK PSH" else "",
      payloadSummary = asciiStr.take(100),
      hexDump = hexStr,
      isThreatAnomaly = isThreat,
      threatDetail = threatDetail
    )

    _totalPacketsCaptured.value += 1
    _totalBytesCaptured.value += length

    val currentList = _packets.value.toMutableList()
    currentList.add(0, captured)
    if (currentList.size > 200) {
      _packets.value = currentList.take(200)
    } else {
      _packets.value = currentList
    }

    updateHostActivity(srcIp, captured.protocol, srcPort, length, isThreat, threatDetail, osFingerprint)
  }

  private fun updateHostActivity(
    ip: String,
    proto: String,
    port: Int,
    bytes: Int,
    isThreat: Boolean = false,
    threatDetail: String = "",
    osGuess: String = ""
  ) {
    if (ip == "0.0.0.0" || ip == "255.255.255.255") return

    val existing = hostMap[ip]
    val currentPorts = existing?.activePorts?.toMutableSet() ?: mutableSetOf()
    if (port > 0) currentPorts.add(port)

    val currentProtos = existing?.protocolsSeen?.toMutableSet() ?: mutableSetOf()
    currentProtos.add(proto)

    val updated = HostTrafficFootprint(
      ip = ip,
      mac = existing?.mac ?: "Resolving...",
      hostName = existing?.hostName?.ifBlank { "Host-$ip" } ?: "Host-$ip",
      osGuess = if (osGuess.isNotBlank()) osGuess else (existing?.osGuess ?: "Generic Host"),
      totalPackets = (existing?.totalPackets ?: 0) + 1,
      totalBytes = (existing?.totalBytes ?: 0L) + bytes,
      activePorts = currentPorts.toList().sorted(),
      protocolsSeen = currentProtos.toList(),
      lastSeen = System.currentTimeMillis(),
      isSuspicious = isThreat || (existing?.isSuspicious ?: false),
      reason = if (threatDetail.isNotBlank()) threatDetail else (existing?.reason ?: "")
    )

    hostMap[ip] = updated
    _hostFootprints.value = hostMap.toMap()
  }

  /**
   * Injects an active test probe into local subnet to stimulate packet reflections.
   */
  fun sendStimulationProbe(targetIp: String, port: Int = 8888) {
    scope.launch {
      try {
        val socket = DatagramSocket()
        val data = "AEGIS_TACTICAL_PACKET_PROBE:${System.currentTimeMillis()}".toByteArray()
        val packet = DatagramPacket(data, data.size, InetAddress.getByName(targetIp), port)
        socket.send(packet)
        socket.close()
      } catch (e: Exception) {
        Log.w("PacketEngine", "Stimulation probe failed: ${e.message}")
      }
    }
  }
}
