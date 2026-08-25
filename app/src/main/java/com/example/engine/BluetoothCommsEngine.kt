package com.example.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class TacticalMessageType {
  CHAT,
  INTEL_ALERT,
  HEARTBEAT,
  DEVICE_SYNC,
  SYSTEM_STATUS
}

data class TacticalMessage(
  val id: String = UUID.randomUUID().toString().take(8),
  val timestamp: Long = System.currentTimeMillis(),
  val formattedTime: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
  val senderName: String,
  val senderAddress: String,
  val content: String,
  val type: TacticalMessageType = TacticalMessageType.CHAT,
  val isLocalUser: Boolean = false,
  val payloadJson: String = ""
)

data class BluetoothPeer(
  val name: String,
  val address: String,
  val bondState: String,
  val rssi: Int = -60,
  val isAegisCapable: Boolean = false
)

data class BluetoothCommsState(
  val isBluetoothEnabled: Boolean = false,
  val isServerListening: Boolean = false,
  val isConnected: Boolean = false,
  val connectedPeerName: String = "",
  val connectedPeerAddress: String = "",
  val isScanning: Boolean = false,
  val peers: List<BluetoothPeer> = emptyList(),
  val messages: List<TacticalMessage> = emptyList(),
  val channelId: String = "TACTICAL-MESH-ALPHA",
  val statusMessage: String = "BT Comms Standby"
)

class BluetoothCommsEngine(private val context: Context) {

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val _state = MutableStateFlow(
    BluetoothCommsState(isBluetoothEnabled = bluetoothAdapter?.isEnabled == true)
  )
  val state: StateFlow<BluetoothCommsState> = _state.asStateFlow()

  private val TACTICAL_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPort SPP
  private val SERVICE_NAME = "AegisTacticalMesh"

  private var serverSocket: BluetoothServerSocket? = null
  private var clientSocket: BluetoothSocket? = null
  private var connectedSocket: BluetoothSocket? = null

  private var inputStream: InputStream? = null
  private var outputStream: OutputStream? = null

  private var listenerJob: Job? = null
  private var readerJob: Job? = null

  private val discoveredPeersMap = mutableMapOf<String, BluetoothPeer>()

  private val receiver = object : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        BluetoothDevice.ACTION_FOUND -> {
          val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
          device?.let { dev ->
            val name = dev.name ?: "Unknown Device"
            val addr = dev.address ?: ""
            if (addr.isNotBlank()) {
              val peer = BluetoothPeer(
                name = name,
                address = addr,
                bondState = if (dev.bondState == BluetoothDevice.BOND_BONDED) "PAIRED" else "UNBONDED",
                rssi = rssi.toInt(),
                isAegisCapable = name.contains("AEGIS", true) || name.contains("TACTICAL", true)
              )
              discoveredPeersMap[addr] = peer
              _state.value = _state.value.copy(peers = discoveredPeersMap.values.toList())
            }
          }
        }
        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
          _state.value = _state.value.copy(isScanning = false)
        }
      }
    }
  }

  init {
    val filter = IntentFilter().apply {
      addAction(BluetoothDevice.ACTION_FOUND)
      addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }
    runCatching { context.registerReceiver(receiver, filter) }
    refreshPairedDevices()
  }

  @SuppressLint("MissingPermission")
  fun refreshPairedDevices() {
    try {
      bluetoothAdapter?.bondedDevices?.forEach { dev ->
        val peer = BluetoothPeer(
          name = dev.name ?: "Paired Node",
          address = dev.address ?: "",
          bondState = "PAIRED",
          rssi = -50,
          isAegisCapable = dev.name?.contains("AEGIS", true) == true
        )
        discoveredPeersMap[dev.address] = peer
      }
      _state.value = _state.value.copy(
        isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
        peers = discoveredPeersMap.values.toList()
      )
    } catch (e: Exception) {
      Log.w("BTComms", "Error fetching paired devices: ${e.message}")
    }
  }

  @SuppressLint("MissingPermission")
  fun startServer() {
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
      _state.value = _state.value.copy(statusMessage = "Bluetooth is not enabled on this device")
      return
    }

    stopServer()

    listenerJob = scope.launch {
      try {
        _state.value = _state.value.copy(
          isServerListening = true,
          statusMessage = "Listening for tactical mesh connections..."
        )

        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, TACTICAL_UUID)

        while (isActive && _state.value.isServerListening) {
          try {
            val socket = serverSocket?.accept()
            if (socket != null) {
              handleIncomingConnection(socket)
              break // connected
            }
          } catch (e: Exception) {
            if (!_state.value.isServerListening) break
          }
        }
      } catch (e: Exception) {
        _state.value = _state.value.copy(
          isServerListening = false,
          statusMessage = "Server initialization failed: ${e.message}"
        )
      }
    }
  }

  fun stopServer() {
    _state.value = _state.value.copy(isServerListening = false)
    listenerJob?.cancel()
    listenerJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  @SuppressLint("MissingPermission")
  fun connectToPeer(address: String) {
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

    scope.launch {
      _state.value = _state.value.copy(statusMessage = "Connecting to $address...")
      try {
        val device = bluetoothAdapter.getRemoteDevice(address)
        clientSocket = device.createRfcommSocketToServiceRecord(TACTICAL_UUID)
        bluetoothAdapter.cancelDiscovery()

        clientSocket?.connect()
        clientSocket?.let { handleIncomingConnection(it, device.name ?: address) }
      } catch (e: Exception) {
        _state.value = _state.value.copy(
          isConnected = false,
          statusMessage = "Connection failed: ${e.message}"
        )
        disconnect()
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleIncomingConnection(socket: BluetoothSocket, remoteName: String? = null) {
    connectedSocket = socket
    val devName = remoteName ?: socket.remoteDevice?.name ?: socket.remoteDevice?.address ?: "Tactical Node"
    val devAddr = socket.remoteDevice?.address ?: "00:00:00:00:00:00"

    try {
      inputStream = socket.inputStream
      outputStream = socket.outputStream

      _state.value = _state.value.copy(
        isConnected = true,
        isServerListening = false,
        connectedPeerName = devName,
        connectedPeerAddress = devAddr,
        statusMessage = "SECURE MESH LINK ESTABLISHED: $devName"
      )

      // Send initial handshake message
      sendMessage("TACTICAL NODE ONLINE // CHANNEL: ${_state.value.channelId}", TacticalMessageType.SYSTEM_STATUS)

      startSocketReader()
    } catch (e: Exception) {
      disconnect()
    }
  }

  private fun startSocketReader() {
    readerJob = scope.launch {
      val buffer = ByteArray(4096)
      while (isActive && _state.value.isConnected) {
        try {
          val inStream = inputStream ?: break
          val bytesRead = inStream.read(buffer)
          if (bytesRead > 0) {
            val rawMsg = String(buffer, 0, bytesRead, Charsets.UTF_8)
            parseIncomingPayload(rawMsg)
          } else if (bytesRead == -1) {
            // Stream closed
            break
          }
        } catch (e: Exception) {
          break
        }
      }
      disconnect()
    }
  }

  private fun parseIncomingPayload(raw: String) {
    try {
      val json = JSONObject(raw)
      val sender = json.optString("sender", _state.value.connectedPeerName)
      val addr = json.optString("addr", _state.value.connectedPeerAddress)
      val content = json.optString("content", raw)
      val typeStr = json.optString("type", "CHAT")
      val type = runCatching { TacticalMessageType.valueOf(typeStr) }.getOrDefault(TacticalMessageType.CHAT)
      val payload = json.optString("payload", "")

      val msg = TacticalMessage(
        senderName = sender,
        senderAddress = addr,
        content = content,
        type = type,
        isLocalUser = false,
        payloadJson = payload
      )

      val list = _state.value.messages.toMutableList()
      list.add(msg)
      _state.value = _state.value.copy(messages = list)
    } catch (e: Exception) {
      // Raw string fallback
      val msg = TacticalMessage(
        senderName = _state.value.connectedPeerName,
        senderAddress = _state.value.connectedPeerAddress,
        content = raw,
        type = TacticalMessageType.CHAT,
        isLocalUser = false
      )
      val list = _state.value.messages.toMutableList()
      list.add(msg)
      _state.value = _state.value.copy(messages = list)
    }
  }

  @SuppressLint("MissingPermission")
  fun sendMessage(content: String, type: TacticalMessageType = TacticalMessageType.CHAT, payloadJson: String = "") {
    val localName = bluetoothAdapter?.name ?: "Local Aegis Host"
    val localAddr = bluetoothAdapter?.address ?: "Local Device"

    val localMsg = TacticalMessage(
      senderName = localName,
      senderAddress = localAddr,
      content = content,
      type = type,
      isLocalUser = true,
      payloadJson = payloadJson
    )

    // Append to local list
    val list = _state.value.messages.toMutableList()
    list.add(localMsg)
    _state.value = _state.value.copy(messages = list)

    // Send over wire if connected
    if (_state.value.isConnected && outputStream != null) {
      scope.launch {
        try {
          val json = JSONObject().apply {
            put("sender", localName)
            put("addr", localAddr)
            put("content", content)
            put("type", type.name)
            put("payload", payloadJson)
            put("timestamp", System.currentTimeMillis())
          }
          outputStream?.write(json.toString().toByteArray(Charsets.UTF_8))
          outputStream?.flush()
        } catch (e: Exception) {
          Log.w("BTComms", "Send message failed: ${e.message}")
        }
      }
    }
  }

  fun broadcastIntelThreat(name: String, mac: String, threatScore: Int, reason: String) {
    val alertText = "SURVEILLANCE THREAT SIGHTING: $name ($mac) | Score: $threatScore/100 | $reason"
    val payload = JSONObject().apply {
      put("name", name)
      put("mac", mac)
      put("threatScore", threatScore)
      put("reason", reason)
    }.toString()
    sendMessage(alertText, TacticalMessageType.INTEL_ALERT, payload)
  }

  @SuppressLint("MissingPermission")
  fun startPeerDiscovery() {
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
    try {
      if (bluetoothAdapter.isDiscovering) {
        bluetoothAdapter.cancelDiscovery()
      }
      bluetoothAdapter.startDiscovery()
      _state.value = _state.value.copy(isScanning = true, statusMessage = "Discovering Bluetooth RFCOMM nodes...")
    } catch (e: Exception) {
      Log.w("BTComms", "Discovery failed: ${e.message}")
    }
  }

  fun disconnect() {
    readerJob?.cancel()
    readerJob = null
    runCatching { inputStream?.close() }
    runCatching { outputStream?.close() }
    runCatching { connectedSocket?.close() }
    runCatching { clientSocket?.close() }
    connectedSocket = null
    clientSocket = null
    inputStream = null
    outputStream = null

    _state.value = _state.value.copy(
      isConnected = false,
      connectedPeerName = "",
      connectedPeerAddress = "",
      statusMessage = "Link disconnected"
    )
  }

  fun cleanup() {
    disconnect()
    stopServer()
    runCatching { context.unregisterReceiver(receiver) }
  }
}
