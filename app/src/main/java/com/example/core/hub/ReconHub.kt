package com.example.core.hub

import android.content.Context
import com.example.core.database.AppDatabase
import com.example.core.database.ReconRepository
import com.example.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReconHub private constructor(
  val context: Context,
  val database: AppDatabase
) {

  val repository = ReconRepository(database, context)
  private val hubScope = CoroutineScope(Dispatchers.IO + Job())

  companion object {
    @Volatile
    private var instance: ReconHub? = null

    fun initialize(context: Context, database: AppDatabase): ReconHub {
      return instance ?: synchronized(this) {
        instance ?: ReconHub(context.applicationContext, database).also { instance = it }
      }
    }

    fun getInstance(): ReconHub {
      return instance ?: throw IllegalStateException("ReconHub has not been initialized yet")
    }
  }

  // Reactive State Streams
  private val _fusionState = MutableStateFlow(
    FusionState(
      threatLevel = ThreatLevel.NOMINAL,
      activeAlerts = emptyList(),
      rfDeviceCount = 0,
      bleTrackerCount = 0,
      subnetHostCount = 0,
      rtspCameraCount = 0,
      emMagnitudeUt = 45f,
      emSpikeActive = false,
      glintCount = 0,
      ultrasonicActive = false,
      acousticPeakHz = 0,
      lastUpdateMs = System.currentTimeMillis()
    )
  )
  val fusionState: StateFlow<FusionState> = _fusionState.asStateFlow()

  private val _rfDevices = MutableStateFlow<List<RfDevice>>(emptyList())
  val rfDevices: StateFlow<List<RfDevice>> = _rfDevices.asStateFlow()

  private val _subnetHosts = MutableStateFlow<List<SubnetHost>>(emptyList())
  val subnetHosts: StateFlow<List<SubnetHost>> = _subnetHosts.asStateFlow()

  private val _protoServices = MutableStateFlow<List<ProtoService>>(emptyList())
  val protoServices: StateFlow<List<ProtoService>> = _protoServices.asStateFlow()

  private val _bleTrackers = MutableStateFlow<List<BleTracker>>(emptyList())
  val bleTrackers: StateFlow<List<BleTracker>> = _bleTrackers.asStateFlow()

  private val _emReading = MutableStateFlow(EmReading())
  val emReading: StateFlow<EmReading> = _emReading.asStateFlow()

  private val _glints = MutableStateFlow<List<GlintPoint>>(emptyList())
  val glints: StateFlow<List<GlintPoint>> = _glints.asStateFlow()

  private val _acoustic = MutableStateFlow(AcousticSpectrum())
  val acoustic: StateFlow<AcousticSpectrum> = _acoustic.asStateFlow()

  private val _terminalLogs = MutableStateFlow<List<String>>(
    listOf(
      "[SYS] RED EYE AEGIS v1.4 Passive Recon Matrix Online",
      "[SYS] Zero-mock multi-sensor hardware engine engaged",
      "[NET] Local Room DB forensic integrity vault ready"
    )
  )
  val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

  fun updateRfDevices(devices: List<RfDevice>) {
    _rfDevices.value = devices
    recomputeFusion()
    hubScope.launch {
      devices.take(15).forEach { rf ->
        repository.logSignal(
          signalType = if (rf.type == RfType.WIFI) "WIFI_BEACON" else "BLE_ADV",
          identifier = rf.id,
          displayName = if (rf.name.isNotBlank()) rf.name else "[Hidden]",
          freqOrChan = "${rf.frequencyMhz} MHz (Ch ${rf.channel})",
          rssi = rf.rssi,
          intensity = (rf.rssi + 100).coerceIn(0, 100).toFloat(),
          threatScore = if (rf.name.contains("cam", true) || rf.name.contains("spy", true)) 85 else 10,
          protocol = "802.11 ${rf.capabilities}"
        )
      }
    }
  }

  fun updateSubnetHosts(hosts: List<SubnetHost>) {
    _subnetHosts.value = hosts
    recomputeFusion()
    hubScope.launch {
      hosts.take(15).forEach { host ->
        repository.logSignal(
          signalType = "SUBNET_ARP",
          identifier = host.ip,
          displayName = if (host.hostName.isNotBlank()) host.hostName else host.ip,
          freqOrChan = "IP/LAN",
          rssi = -55,
          intensity = 70f,
          threatScore = if (host.isRtspCamera) 95 else 10,
          protocol = "TCP/IP"
        )
      }
    }
  }

  fun updateProtoServices(services: List<ProtoService>) {
    _protoServices.value = services
    recomputeFusion()
    hubScope.launch {
      services.take(10).forEach { svc ->
        repository.logSignal(
          signalType = "MDNS_SSDP",
          identifier = "${svc.host}:${svc.port}",
          displayName = svc.serviceType,
          freqOrChan = svc.serviceType,
          rssi = -60,
          intensity = 65f,
          threatScore = if (svc.serviceType.contains("camera", true)) 90 else 15,
          protocol = svc.protocol
        )
      }
    }
  }

  fun updateBleTrackers(trackers: List<BleTracker>) {
    _bleTrackers.value = trackers
    recomputeFusion()
    hubScope.launch {
      trackers.take(15).forEach { tracker ->
        repository.logSignal(
          signalType = "BLE_ADV",
          identifier = tracker.mac,
          displayName = "${tracker.name} (${tracker.brand.name})",
          freqOrChan = "2.4 GHz BLE",
          rssi = tracker.rssi,
          intensity = (tracker.rssi + 100).coerceIn(0, 100).toFloat(),
          threatScore = if (tracker.isFollowingThreat) 95 else 20,
          protocol = "Bluetooth LE Advert"
        )
      }
    }
  }

  fun updateEmReading(reading: EmReading) {
    _emReading.value = reading
    recomputeFusion()
    if (reading.isSpike) {
      hubScope.launch {
        repository.logSignal(
          signalType = "EM_FLUX",
          identifier = "MAG_SENSOR",
          displayName = "Electromagnetic Flux Spike",
          freqOrChan = "B-Field (uT)",
          rssi = -reading.magnitudeUt.toInt(),
          intensity = reading.magnitudeUt,
          threatScore = 75,
          protocol = "Magnetic Hall Effect"
        )
      }
    }
  }

  fun updateGlints(glints: List<GlintPoint>) {
    _glints.value = glints
    recomputeFusion()
  }

  fun updateAcoustic(spectrum: AcousticSpectrum) {
    _acoustic.value = spectrum
    recomputeFusion()
    if (spectrum.isUltrasonicActive) {
      hubScope.launch {
        repository.logSignal(
          signalType = "ACOUSTIC",
          identifier = "${spectrum.peakHz}Hz",
          displayName = "Ultrasonic Carrier Peak",
          freqOrChan = "${spectrum.peakHz} Hz",
          rssi = -40,
          intensity = spectrum.peakMagnitude,
          threatScore = 80,
          protocol = "FFT Audio Spectrum"
        )
      }
    }
  }

  fun logMessage(tag: String, message: String) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    val entry = "[$time] [$tag] $message"
    val list = _terminalLogs.value.toMutableList()
    list.add(0, entry)
    if (list.size > 80) list.removeAt(list.size - 1)
    _terminalLogs.value = list

    hubScope.launch {
      repository.logSystemEvent(
        subsystem = tag,
        tag = tag,
        message = message,
        level = if (message.contains("ALERT") || message.contains("CRITICAL")) "ALERT" else "INFO"
      )
    }
  }

  private fun recomputeFusion() {
    val rfs = _rfDevices.value
    val hosts = _subnetHosts.value
    val protos = _protoServices.value
    val trackers = _bleTrackers.value
    val em = _emReading.value
    val glints = _glints.value
    val audio = _acoustic.value

    val alerts = mutableListOf<ThreatAlert>()
    var maxLevel = ThreatLevel.NOMINAL

    // 1. RTSP port 554 camera detection -> CRITICAL
    val rtspCameras = hosts.filter { it.isRtspCamera || 554 in it.openPorts }
    rtspCameras.forEach { cam ->
      alerts.add(
        ThreatAlert(
          id = "rtsp-${cam.ip}",
          level = ThreatLevel.CRITICAL,
          title = "RTSP Video Stream Active",
          details = "Surveillance stream detected at ${cam.ip}:554 (${cam.vendorGuess})",
          sourceModule = "SUBNET"
        )
      )
      maxLevel = ThreatLevel.CRITICAL
    }

    // 2. Protocol discovery ONVIF/RTSP cameras -> CRITICAL
    val camProtos = protos.filter {
      it.serviceType.contains("onvif", true) ||
          it.serviceType.contains("rtsp", true) ||
          it.vendorGuess.contains("camera", true)
    }
    camProtos.forEach { p ->
      alerts.add(
        ThreatAlert(
          id = "proto-${p.id}",
          level = ThreatLevel.CRITICAL,
          title = "ONVIF/SSDP Camera Broadcast",
          details = "${p.protocol}: ${p.info} at ${p.host}:${p.port}",
          sourceModule = "PROTO"
        )
      )
      maxLevel = ThreatLevel.CRITICAL
    }

    // 3. Stalker beacons following user -> CRITICAL
    val following = trackers.filter { it.isFollowingThreat }
    following.forEach { tracker ->
      alerts.add(
        ThreatAlert(
          id = "tracker-${tracker.mac}",
          level = ThreatLevel.CRITICAL,
          title = "Persistent Stalker Beacon Following",
          details = "${tracker.name} (${tracker.brand.name}) sighted ${tracker.sightingCount} times",
          sourceModule = "TRACKERS"
        )
      )
      maxLevel = ThreatLevel.CRITICAL
    }

    // 4. EM Magnetic Spike -> Concealed active electronics
    if (em.isSpike) {
      val level = if (rfs.any { it.rssi > -50 } || rtspCameras.isNotEmpty()) ThreatLevel.CRITICAL else ThreatLevel.WARN
      alerts.add(
        ThreatAlert(
          id = "em-spike",
          level = level,
          title = if (level == ThreatLevel.CRITICAL) "Concealed Electronic Hardware Suspected" else "Magnetic Flux Spike Anomaly",
          details = "Ambient flux %.1f uT exceeds baseline %.1f uT (+%.1f uT)".format(em.magnitudeUt, em.baselineUt, em.deltaUt),
          sourceModule = "EM_SWEEP"
        )
      )
      if (maxLevel != ThreatLevel.CRITICAL) maxLevel = level
    }

    // 5. Optical Lens Glint -> WARN
    if (glints.isNotEmpty()) {
      alerts.add(
        ThreatAlert(
          id = "glint-detected",
          level = ThreatLevel.WARN,
          title = "Optical Lens Specular Reflection",
          details = "${glints.size} pinpoint optical reflections detected in field of view",
          sourceModule = "IR_GLINT"
        )
      )
      if (maxLevel == ThreatLevel.NOMINAL) maxLevel = ThreatLevel.WARN
    }

    // 6. Ultrasonic Audio Beacon -> WARN
    if (audio.isUltrasonicActive) {
      alerts.add(
        ThreatAlert(
          id = "acoustic-ultrasonic",
          level = ThreatLevel.WARN,
          title = "Concealed Ultrasonic Audio Beacon",
          details = "Acoustic emission at ${audio.peakHz} Hz (>17 kHz tracking frequency)",
          sourceModule = "ACOUSTIC"
        )
      )
      if (maxLevel == ThreatLevel.NOMINAL) maxLevel = ThreatLevel.WARN
    }

    _fusionState.value = FusionState(
      threatLevel = maxLevel,
      activeAlerts = alerts,
      rfDeviceCount = rfs.size,
      bleTrackerCount = trackers.size,
      subnetHostCount = hosts.size,
      rtspCameraCount = rtspCameras.size + camProtos.size,
      emMagnitudeUt = em.magnitudeUt,
      emSpikeActive = em.isSpike,
      glintCount = glints.size,
      ultrasonicActive = audio.isUltrasonicActive,
      acousticPeakHz = audio.peakHz,
      lastUpdateMs = System.currentTimeMillis()
    )
  }

  fun generateEvidenceDigest(): String {
    val state = _fusionState.value
    val raw = "${System.currentTimeMillis()}|${state.threatLevel}|${state.rfDeviceCount}|${state.subnetHostCount}|${state.emMagnitudeUt}|${state.activeAlerts.size}"
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(raw.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }
}
