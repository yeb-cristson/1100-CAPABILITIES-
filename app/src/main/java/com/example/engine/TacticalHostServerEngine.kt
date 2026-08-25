package com.example.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.core.database.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ConnectedClient(
  val ip: String,
  val userAgent: String,
  val firstConnected: Long = System.currentTimeMillis(),
  val lastActive: Long = System.currentTimeMillis(),
  val requestCount: Int = 1
)

data class TacticalHostState(
  val isRunning: Boolean = false,
  val port: Int = 8888,
  val hostIp: String = "127.0.0.1",
  val hostUrl: String = "http://127.0.0.1:8888",
  val isNsdBroadcasting: Boolean = false,
  val nsdServiceName: String = "Aegis-Tactical-Host",
  val totalRequests: Long = 0L,
  val activeClients: List<ConnectedClient> = emptyList(),
  val lastBroadcastMessage: String = "",
  val logs: List<String> = emptyList()
)

class TacticalHostServerEngine(
  private val context: Context,
  private val database: AppDatabase,
  private val packetEngine: PacketAnalysisEngine? = null,
  private val commsEngine: BluetoothCommsEngine? = null
) {
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private val _state = MutableStateFlow(TacticalHostState())
  val state: StateFlow<TacticalHostState> = _state.asStateFlow()

  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private val clientsMap = ConcurrentHashMap<String, ConnectedClient>()

  private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
  private var registrationListener: NsdManager.RegistrationListener? = null

  private val SERVICE_TYPE = "_aegis-recon._tcp."
  private val SERVICE_NAME = "Aegis-Tactical-Host"

  fun startServer(port: Int = 8888) {
    if (_state.value.isRunning) return

    val ip = getLocalIpAddress() ?: "127.0.0.1"
    val url = "http://$ip:$port"

    serverJob = scope.launch {
      try {
        serverSocket = ServerSocket().apply {
          reuseAddress = true
          bind(InetSocketAddress(port))
        }

        _state.value = _state.value.copy(
          isRunning = true,
          port = port,
          hostIp = ip,
          hostUrl = url,
          logs = _state.value.logs + "[${getTimestamp()}] Tactical Host Server bound to $url"
        )

        // Start mDNS / NSD network broadcast so local nodes find it
        startNsdBroadcast(port)

        while (isActive && _state.value.isRunning) {
          try {
            val clientSocket = serverSocket?.accept() ?: break
            launch { handleClientRequest(clientSocket) }
          } catch (e: Exception) {
            if (!_state.value.isRunning) break
          }
        }
      } catch (e: Exception) {
        Log.e("TacticalHost", "Server startup error: ${e.message}", e)
        _state.value = _state.value.copy(
          isRunning = false,
          logs = _state.value.logs + "[${getTimestamp()}] Server error: ${e.message}"
        )
      }
    }
  }

  fun stopServer() {
    _state.value = _state.value.copy(
      isRunning = false,
      logs = _state.value.logs + "[${getTimestamp()}] Tactical Host Server terminated"
    )
    stopNsdBroadcast()
    serverJob?.cancel()
    serverJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  private fun startNsdBroadcast(port: Int) {
    try {
      val serviceInfo = NsdServiceInfo().apply {
        serviceName = SERVICE_NAME
        serviceType = SERVICE_TYPE
        setPort(port)
      }

      registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
          _state.value = _state.value.copy(
            isNsdBroadcasting = true,
            nsdServiceName = serviceInfo?.serviceName ?: SERVICE_NAME,
            logs = _state.value.logs + "[${getTimestamp()}] mDNS/NSD broadcast active: ${serviceInfo?.serviceName}"
          )
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
          _state.value = _state.value.copy(isNsdBroadcasting = false)
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
          _state.value = _state.value.copy(isNsdBroadcasting = false)
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
          _state.value = _state.value.copy(isNsdBroadcasting = false)
        }
      }

      nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    } catch (e: Exception) {
      Log.w("TacticalHost", "NSD register failed: ${e.message}")
    }
  }

  private fun stopNsdBroadcast() {
    try {
      registrationListener?.let { nsdManager?.unregisterService(it) }
      registrationListener = null
      _state.value = _state.value.copy(isNsdBroadcasting = false)
    } catch (e: Exception) {
      Log.w("TacticalHost", "NSD unregister failed: ${e.message}")
    }
  }

  private suspend fun handleClientRequest(socket: Socket) = withContext(Dispatchers.IO) {
    val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
    var userAgent = "Generic Client"

    try {
      val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
      val out = socket.getOutputStream()

      val requestLine = reader.readLine() ?: return@withContext
      val tokens = requestLine.split(" ")
      if (tokens.size < 2) return@withContext

      val method = tokens[0].uppercase()
      val path = tokens[1].substringBefore("?")

      // Read headers
      var contentLength = 0
      var line = reader.readLine()
      while (!line.isNullOrEmpty()) {
        if (line.startsWith("User-Agent:", ignoreCase = true)) {
          userAgent = line.substringAfter(":").trim()
        } else if (line.startsWith("Content-Length:", ignoreCase = true)) {
          contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
        }
        line = reader.readLine()
      }

      // Read body if POST
      var body = ""
      if (method == "POST" && contentLength > 0) {
        val bodyChars = CharArray(contentLength)
        reader.read(bodyChars, 0, contentLength)
        body = String(bodyChars)
      }

      // Update client session record
      updateClient(clientIp, userAgent)
      _state.value = _state.value.copy(totalRequests = _state.value.totalRequests + 1)

      // Route Dispatch
      when {
        path == "/" || path == "/index.html" -> serveTacticalWebDashboard(out)
        path == "/api/status" -> serveStatusJson(out)
        path == "/api/devices" -> serveDevicesJson(out)
        path == "/api/packets" -> servePacketsJson(out)
        path == "/api/broadcast" && method == "POST" -> handleBroadcastPost(body, out)
        path == "/api/ping" -> serveJson(out, 200, JSONObject().put("status", "PONG").put("time", System.currentTimeMillis()).toString())
        else -> serveNotFound(out)
      }
    } catch (e: Exception) {
      Log.d("TacticalHost", "Client request error: ${e.message}")
    } finally {
      runCatching { socket.close() }
    }
  }

  private fun updateClient(ip: String, userAgent: String) {
    val existing = clientsMap[ip]
    val updated = if (existing != null) {
      existing.copy(
        lastActive = System.currentTimeMillis(),
        requestCount = existing.requestCount + 1,
        userAgent = userAgent.ifBlank { existing.userAgent }
      )
    } else {
      ConnectedClient(ip = ip, userAgent = userAgent)
    }
    clientsMap[ip] = updated
    _state.value = _state.value.copy(activeClients = clientsMap.values.toList())
  }

  private fun serveTacticalWebDashboard(out: OutputStream) {
    val html = """
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>AEGIS // Tactical LAN Recon Console</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Courier New', monospace; }
          body { background: #07090C; color: #E4E4E7; padding: 16px; }
          header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #00E5FF; padding-bottom: 12px; margin-bottom: 16px; }
          h1 { font-size: 18px; color: #00E5FF; letter-spacing: 1px; }
          .tag { background: rgba(0,229,255,0.15); color: #00E5FF; padding: 4px 8px; border-radius: 4px; font-size: 12px; border: 1px solid #00E5FF; }
          .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px; margin-bottom: 16px; }
          .card { background: #0E1318; border: 1px solid #1E293B; border-radius: 6px; padding: 14px; }
          .card h2 { font-size: 13px; color: #A1A1AA; margin-bottom: 8px; border-bottom: 1px solid #1E293B; padding-bottom: 4px; }
          .metric { font-size: 24px; font-weight: bold; color: #00E676; }
          .danger { color: #FF2A3C; }
          .warn { color: #FF9800; }
          table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 11px; }
          th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #1E293B; }
          th { color: #00E5FF; }
          .badge { padding: 2px 6px; border-radius: 3px; font-size: 9px; font-weight: bold; }
          .badge-cam { background: rgba(255,42,60,0.2); color: #FF2A3C; border: 1px solid #FF2A3C; }
          .badge-phone { background: rgba(0,229,255,0.2); color: #00E5FF; border: 1px solid #00E5FF; }
          .badge-iot { background: rgba(255,152,0,0.2); color: #FF9800; border: 1px solid #FF9800; }
          .broadcast-box { display: flex; gap: 8px; margin-top: 10px; }
          input { background: #07090C; border: 1px solid #00E5FF; color: white; padding: 8px; border-radius: 4px; flex: 1; font-family: monospace; }
          button { background: #00E5FF; color: black; border: none; padding: 8px 16px; border-radius: 4px; font-weight: bold; cursor: pointer; font-family: monospace; }
          button:hover { background: #6effff; }
        </style>
      </head>
      <body>
        <header>
          <div>
            <h1>AEGIS TACTICAL RECON SERVER</h1>
            <div style="font-size: 11px; color: #71717A;">NODE: ${_state.value.hostUrl} // MDNS: ${_state.value.nsdServiceName}</div>
          </div>
          <span class="tag">ACTIVE LAN HOST</span>
        </header>

        <div class="grid">
          <div class="card">
            <h2>TACTICAL BROADCAST CHANNEL</h2>
            <div class="broadcast-box">
              <input type="text" id="bcastMsg" placeholder="Send network broadcast alert...">
              <button onclick="sendBroadcast()">BROADCAST</button>
            </div>
            <div id="bcastStatus" style="font-size: 10px; color: #00E676; margin-top: 6px;"></div>
          </div>

          <div class="card">
            <h2>SERVER TELEMETRY</h2>
            <div style="display: flex; justify-content: space-between;">
              <div>
                <div style="font-size: 10px; color: #71717A;">TOTAL REQUESTS</div>
                <div class="metric" id="reqCount">${_state.value.totalRequests}</div>
              </div>
              <div>
                <div style="font-size: 10px; color: #71717A;">ACTIVE CLIENTS</div>
                <div class="metric warn" id="clientCount">${_state.value.activeClients.size}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <h2>LIVE NETWORK DISCOVERED DEVICES & AI CLASSIFICATIONS</h2>
          <table>
            <thead>
              <tr>
                <th>MAC / ID</th>
                <th>IDENTIFIER</th>
                <th>VENDOR</th>
                <th>MEDIUM</th>
                <th>AI CLASSIFICATION</th>
                <th>RISK</th>
              </tr>
            </thead>
            <tbody id="devicesTable">
              <tr><td colspan="6" style="color: #71717A;">Loading live device matrix...</td></tr>
            </tbody>
          </table>
        </div>

        <script>
          async function refreshData() {
            try {
              const res = await fetch('/api/devices');
              const devs = await res.json();
              const tbody = document.getElementById('devicesTable');
              if (devs.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="color:#71717A;">No devices stored in Room vault yet.</td></tr>';
                return;
              }
              tbody.innerHTML = devs.map(d => `
                <tr>
                  <td><code>${'$'}{d.macOrId}</code></td>
                  <td><b>${'$'}{d.name || 'Unknown'}</b></td>
                  <td>${'$'}{d.vendor || 'N/A'}</td>
                  <td>${'$'}{d.medium}</td>
                  <td><span class="badge ${'$'}{d.aiTag === 'Camera' ? 'badge-cam' : d.aiTag === 'Smartphone' ? 'badge-phone' : 'badge-iot'}">${'$'}{d.aiTag} (${'$'}{(d.aiConfidence * 100).toFixed(0)}%)</span></td>
                  <td class="${'$'}{d.riskScore > 50 ? 'danger' : ''}">${'$'}{d.riskScore}/100</td>
                </tr>
              `).join('');
            } catch(e){}
          }

          async function sendBroadcast() {
            const input = document.getElementById('bcastMsg');
            const msg = input.value.trim();
            if (!msg) return;
            try {
              const res = await fetch('/api/broadcast', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: msg, sender: 'Web-Operator' })
              });
              const data = await res.json();
              document.getElementById('bcastStatus').innerText = '✓ Alert broadcasted to tactical mesh';
              input.value = '';
              setTimeout(() => { document.getElementById('bcastStatus').innerText = ''; }, 3000);
            } catch(e) {
              document.getElementById('bcastStatus').innerText = '✗ Broadcast error: ' + e;
            }
          }

          refreshData();
          setInterval(refreshData, 3000);
        </script>
      </body>
      </html>
    """.trimIndent()

    serveHtml(out, 200, html)
  }

  private suspend fun serveDevicesJson(out: OutputStream) = withContext(Dispatchers.IO) {
    val devices = database.discoveredDeviceDao().getAllDevicesSnapshot()
    val array = JSONArray()
    devices.forEach { d ->
      array.put(JSONObject().apply {
        put("macOrId", d.macOrId)
        put("name", d.name)
        put("vendor", d.vendor)
        put("medium", d.medium)
        put("ipAddress", d.ipAddress)
        put("aiTag", d.aiTag)
        put("aiConfidence", d.aiConfidence)
        put("aiReasoning", d.aiInferenceReasoning)
        put("riskScore", d.riskScore)
        put("isSuspect", d.isSuspect)
        put("lastSeen", d.lastSeen)
      })
    }
    serveJson(out, 200, array.toString())
  }

  private fun servePacketsJson(out: OutputStream) {
    val packets = packetEngine?.packets?.value ?: emptyList()
    val array = JSONArray()
    packets.take(50).forEach { p ->
      array.put(JSONObject().apply {
        put("id", p.id)
        put("time", p.formattedTime)
        put("protocol", p.protocol)
        put("sourceIp", p.sourceIp)
        put("destIp", p.destIp)
        put("sourcePort", p.sourcePort)
        put("destPort", p.destPort)
        put("packetLength", p.packetLength)
        put("osFingerprint", p.osFingerprint)
        put("summary", p.payloadSummary)
      })
    }
    serveJson(out, 200, array.toString())
  }

  private fun serveStatusJson(out: OutputStream) {
    val json = JSONObject().apply {
      put("status", "ACTIVE")
      put("hostUrl", _state.value.hostUrl)
      put("nsdName", _state.value.nsdServiceName)
      put("totalRequests", _state.value.totalRequests)
      put("activeClients", _state.value.activeClients.size)
      put("timestamp", System.currentTimeMillis())
    }
    serveJson(out, 200, json.toString())
  }

  private fun handleBroadcastPost(body: String, out: OutputStream) {
    try {
      val json = JSONObject(body)
      val msg = json.optString("message", "Tactical alert")
      val sender = json.optString("sender", "LAN-Client")

      _state.value = _state.value.copy(
        lastBroadcastMessage = "$sender: $msg",
        logs = _state.value.logs + "[${getTimestamp()}] BROADCAST from $sender: $msg"
      )

      // Relay to Bluetooth mesh comms if available
      commsEngine?.sendMessage("LAN BROADCAST [$sender]: $msg", TacticalMessageType.INTEL_ALERT)

      val response = JSONObject().apply {
        put("success", true)
        put("message", "Broadcast received and disseminated")
      }
      serveJson(out, 200, response.toString())
    } catch (e: Exception) {
      val err = JSONObject().apply {
        put("success", false)
        put("error", e.message)
      }
      serveJson(out, 400, err.toString())
    }
  }

  private fun serveHtml(out: OutputStream, statusCode: Int, html: String) {
    val bytes = html.toByteArray(Charsets.UTF_8)
    val header = "HTTP/1.1 $statusCode OK\r\n" +
      "Content-Type: text/html; charset=utf-8\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    out.write(header.toByteArray(Charsets.US_ASCII))
    out.write(bytes)
    out.flush()
  }

  private fun serveJson(out: OutputStream, statusCode: Int, json: String) {
    val bytes = json.toByteArray(Charsets.UTF_8)
    val header = "HTTP/1.1 $statusCode OK\r\n" +
      "Content-Type: application/json; charset=utf-8\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    out.write(header.toByteArray(Charsets.US_ASCII))
    out.write(bytes)
    out.flush()
  }

  private fun serveNotFound(out: OutputStream) {
    val body = "404 Not Found"
    val header = "HTTP/1.1 404 Not Found\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
    out.write(header.toByteArray(Charsets.US_ASCII))
    out.flush()
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

  private fun getTimestamp(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
  }
}
