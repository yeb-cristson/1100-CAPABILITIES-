package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.database.AppDatabase
import com.example.core.engine.*
import com.example.core.evidence.EvidenceManager
import com.example.core.hub.ReconHub
import com.example.engine.AirspaceEngine
import com.example.engine.SubnetEngine
import com.example.engine.EmEngine
import com.example.engine.ProtoEngine
import com.example.engine.RoomProximityEngine
import com.example.engine.LinuxKernelAuditEngine
import com.example.engine.SpatialHeatmapEngine
import com.example.engine.PacketAnalysisEngine
import com.example.engine.BluetoothCommsEngine
import com.example.engine.TacticalHostServerEngine
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private lateinit var airspaceEngine: AirspaceEngine
  private lateinit var subnetEngine: SubnetEngine
  private lateinit var emEngine: EmEngine
  private lateinit var protoEngine: ProtoEngine
  private lateinit var localizeEngine: RfLocalizeEngine
  private lateinit var glintEngine: IrGlintEngine
  private lateinit var acousticEngine: AcousticFftEngine
  private lateinit var trackerEngine: BleTrackerEngine
  private lateinit var roomProximityEngine: RoomProximityEngine
  private lateinit var kernelAuditEngine: LinuxKernelAuditEngine
  private lateinit var spatialHeatmapEngine: SpatialHeatmapEngine
  private lateinit var packetAnalysisEngine: PacketAnalysisEngine
  private lateinit var bluetoothCommsEngine: BluetoothCommsEngine
  private lateinit var tacticalHostServerEngine: TacticalHostServerEngine
  private lateinit var database: AppDatabase
  private lateinit var evidenceManager: EvidenceManager
  private lateinit var reconHub: ReconHub

  private val mainScope = CoroutineScope(Dispatchers.Main)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    database = AppDatabase.getDatabase(this)
    reconHub = ReconHub.initialize(this, database)
    evidenceManager = EvidenceManager(this, database)

    airspaceEngine = AirspaceEngine(this)
    subnetEngine = SubnetEngine(this)
    emEngine = EmEngine(this)
    protoEngine = ProtoEngine(this)
    localizeEngine = RfLocalizeEngine(this)
    glintEngine = IrGlintEngine()
    acousticEngine = AcousticFftEngine()
    trackerEngine = BleTrackerEngine(this)
    roomProximityEngine = RoomProximityEngine(this, reconHub.repository)
    kernelAuditEngine = LinuxKernelAuditEngine(this, reconHub.repository)
    spatialHeatmapEngine = SpatialHeatmapEngine(this, reconHub.repository)
    packetAnalysisEngine = PacketAnalysisEngine(this)
    bluetoothCommsEngine = BluetoothCommsEngine(this)
    tacticalHostServerEngine = TacticalHostServerEngine(this, database, packetAnalysisEngine, bluetoothCommsEngine)

    mainScope.launch {
      airspaceEngine.devices.collect { devices ->
        spatialHeatmapEngine.ingestRfDevices(devices)
      }
    }
    mainScope.launch {
      subnetEngine.hosts.collect { hosts ->
        spatialHeatmapEngine.ingestSubnetHosts(hosts)
      }
    }

    setContent {
      RedEyeTheme {
        RedEyeAppRoot(
          airspaceEngine = airspaceEngine,
          subnetEngine = subnetEngine,
          emEngine = emEngine,
          protoEngine = protoEngine,
          localizeEngine = localizeEngine,
          glintEngine = glintEngine,
          acousticEngine = acousticEngine,
          trackerEngine = trackerEngine,
          roomProximityEngine = roomProximityEngine,
          kernelAuditEngine = kernelAuditEngine,
          spatialHeatmapEngine = spatialHeatmapEngine,
          packetAnalysisEngine = packetAnalysisEngine,
          bluetoothCommsEngine = bluetoothCommsEngine,
          tacticalHostServerEngine = tacticalHostServerEngine,
          evidenceManager = evidenceManager,
          database = database,
          onStartEngines = { startBackgroundEngines() }
        )
      }
    }
  }

  private fun startBackgroundEngines() {
    airspaceEngine.startScan()
    emEngine.start()
    localizeEngine.start()
    trackerEngine.start()
    roomProximityEngine.start()
    spatialHeatmapEngine.start()
  }

  override fun onResume() {
    super.onResume()
    if (hasRequiredPermissions()) {
      startBackgroundEngines()
    }
  }

  override fun onPause() {
    super.onPause()
    airspaceEngine.stopScan()
    emEngine.stop()
    localizeEngine.stop()
    protoEngine.stopDiscovery()
    acousticEngine.stop()
    trackerEngine.stop()
    roomProximityEngine.stop()
    spatialHeatmapEngine.stop()
    packetAnalysisEngine.stopCapture()
  }

  override fun onDestroy() {
    super.onDestroy()
    bluetoothCommsEngine.cleanup()
    tacticalHostServerEngine.stopServer()
  }

  private fun hasRequiredPermissions(): Boolean {
    val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fineLocation
  }
}

@Composable
fun RedEyeAppRoot(
  airspaceEngine: AirspaceEngine,
  subnetEngine: SubnetEngine,
  emEngine: EmEngine,
  protoEngine: ProtoEngine,
  localizeEngine: RfLocalizeEngine,
  glintEngine: IrGlintEngine,
  acousticEngine: AcousticFftEngine,
  trackerEngine: BleTrackerEngine,
  roomProximityEngine: RoomProximityEngine,
  kernelAuditEngine: LinuxKernelAuditEngine,
  spatialHeatmapEngine: SpatialHeatmapEngine,
  packetAnalysisEngine: PacketAnalysisEngine,
  bluetoothCommsEngine: BluetoothCommsEngine,
  tacticalHostServerEngine: TacticalHostServerEngine,
  evidenceManager: EvidenceManager,
  database: AppDatabase,
  onStartEngines: () -> Unit
) {
  var currentDestination by remember { mutableStateOf("FUSION") }
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()

  val permissionsToRequest = remember {
    val list = mutableListOf(
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      list.add(Manifest.permission.BLUETOOTH_SCAN)
      list.add(Manifest.permission.BLUETOOTH_CONNECT)
      list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    list.toTypedArray()
  }

  var isAuthorized by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    val locationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
    if (locationGranted) {
      isAuthorized = true
      onStartEngines()
    }
  }

  LaunchedEffect(Unit) {
    permissionLauncher.launch(permissionsToRequest)
  }

  if (!isAuthorized) {
    PermissionGateScreen(
      onRequestPermissions = {
        permissionLauncher.launch(permissionsToRequest)
      }
    )
    return
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0A0A0A),
        drawerContentColor = Color.White,
        modifier = Modifier.width(300.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SignalRed)
            )
            Text(
              text = "AEGIS MODULES (17)",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              letterSpacing = 1.sp,
              color = Color.White
            )
          }

          HorizontalDivider(color = Color(0x1AFFFFFF))
          Spacer(modifier = Modifier.height(8.dp))

          val modules = listOf(
            Triple("FUSION", "M0: FUSION HUB", Icons.Default.Radar),
            Triple("HEATMAP", "M13: SPATIAL D3 HEATMAP", Icons.Default.Language),
            Triple("AIRSPACE", "M1: AIRSPACE RADAR", Icons.Default.Wifi),
            Triple("SUBNET", "M2: SUBNET HUNTER", Icons.Default.Hub),
            Triple("PACKET_SNIFFER", "M14: RAW SOCKET PACKETS", Icons.Default.GraphicEq),
            Triple("BT_COMMS", "M15: BT REAL-TIME COMMS", Icons.Default.Share),
            Triple("TACTICAL_SERVER", "M16: LAN HOST SERVER", Icons.Default.Storage),
            Triple("EM_SWEEP", "M3: EM FLUX SWEEP", Icons.Default.Sensors),
            Triple("PROTO", "M4: PROTO DISCOVERY", Icons.Default.Visibility),
            Triple("LOCALIZE", "M5: RF LOCALIZE", Icons.Default.CompassCalibration),
            Triple("IR_GLINT", "M6: IR GLINT CAM", Icons.Default.CameraAlt),
            Triple("ACOUSTIC", "M7: ACOUSTIC FFT", Icons.Default.GraphicEq),
            Triple("TRACKERS", "M8: BLE TRACKERS", Icons.Default.GpsFixed),
            Triple("EVIDENCE", "M9: SIGNED EVIDENCE", Icons.Default.Fingerprint),
            Triple("ROOM_RANGE", "M10: ROOM & RANGE RADAR", Icons.Default.NearMe),
            Triple("KERNEL_AUDIT", "M11: KERNEL SECURITY AUDIT", Icons.Default.Terminal),
            Triple("RECON_VAULT", "M12: ROOM RECON VAULT", Icons.Default.Storage)
          )

          modules.forEach { (id, label, icon) ->
            val isSelected = currentDestination == id
            NavigationDrawerItem(
              icon = {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (isSelected) SignalRed else if (id == "HEATMAP" || id == "FUSION" || id == "PACKET_SNIFFER" || id == "TACTICAL_SERVER") RadarCyan else Color(0xFFA1A1AA)
                )
              },
              label = {
                Text(
                  text = label,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else Color(0xFFD4D4D8)
                )
              },
              selected = isSelected,
              onClick = {
                currentDestination = id
                scope.launch { drawerState.close() }
              },
              colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = Color(0x3333090E),
                unselectedContainerColor = Color.Transparent
              ),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.padding(vertical = 2.dp)
            )
          }
        }
      }
    }
  ) {
    Scaffold(
      containerColor = Color(0xFF050505),
      bottomBar = {
        ImmersiveBottomNav(
          currentDestination = currentDestination,
          onSelectDestination = { dest ->
            currentDestination = dest
          },
          onOpenMore = {
            scope.launch { drawerState.open() }
          }
        )
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        when (currentDestination) {
          "FUSION" -> FusionDashboardScreen(
            hub = ReconHub.getInstance(),
            onOpenGlint = { currentDestination = "IR_GLINT" },
            onNavigateToModule = { currentDestination = it },
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "HEATMAP" -> SpatialHeatmapScreen(
            engine = spatialHeatmapEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "AIRSPACE" -> AirspaceRadarScreen(
            engine = airspaceEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "SUBNET" -> SubnetHunterScreen(
            engine = subnetEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNavigateToPacketAnalyzer = { currentDestination = "PACKET_SNIFFER" }
          )
          "PACKET_SNIFFER" -> PacketAnalysisScreen(
            engine = packetAnalysisEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "BT_COMMS" -> BluetoothCommsScreen(
            engine = bluetoothCommsEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "TACTICAL_SERVER" -> TacticalHostServerScreen(
            engine = tacticalHostServerEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "EM_SWEEP" -> EmSweeperScreen(
            engine = emEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "PROTO" -> ProtoDiscoveryScreen(
            engine = protoEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "LOCALIZE" -> RfLocalizeScreen(
            localizeEngine = localizeEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "IR_GLINT" -> IrGlintScreen(
            glintEngine = glintEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "ACOUSTIC" -> AcousticFftScreen(
            acousticEngine = acousticEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "TRACKERS" -> BleTrackersScreen(
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "EVIDENCE" -> EvidenceScreen(
            evidenceManager = evidenceManager,
            fusionState = ReconHub.getInstance().fusionState.collectAsState().value,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "ROOM_RANGE" -> RoomRangeScreen(
            engine = roomProximityEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "KERNEL_AUDIT" -> LinuxKernelAuditScreen(
            engine = kernelAuditEngine,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          "RECON_VAULT" -> PersistentReconLogsScreen(
            repository = ReconHub.getInstance().repository,
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
          else -> FusionDashboardScreen(
            hub = ReconHub.getInstance(),
            onOpenGlint = { currentDestination = "IR_GLINT" },
            onNavigateToModule = { currentDestination = it },
            onOpenDrawer = { scope.launch { drawerState.open() } }
          )
        }
      }
    }
  }
}

@Composable
fun ImmersiveBottomNav(
  currentDestination: String,
  onSelectDestination: (String) -> Unit,
  onOpenMore: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(68.dp)
      .background(Color(0xFF0A0A0A))
      .border(width = 1.dp, color = Color(0x1AFFFFFF))
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically
  ) {
    NavIconButton(
      label = "FUSION",
      icon = Icons.Default.Bolt,
      isSelected = currentDestination == "FUSION",
      onClick = { onSelectDestination("FUSION") }
    )
    NavIconButton(
      label = "HEATMAP",
      icon = Icons.Default.Language,
      isSelected = currentDestination == "HEATMAP",
      onClick = { onSelectDestination("HEATMAP") }
    )
    NavIconButton(
      label = "AIRSPACE",
      icon = Icons.Default.Wifi,
      isSelected = currentDestination == "AIRSPACE",
      onClick = { onSelectDestination("AIRSPACE") }
    )
    NavIconButton(
      label = "ROOM RADAR",
      icon = Icons.Default.NearMe,
      isSelected = currentDestination == "ROOM_RANGE",
      onClick = { onSelectDestination("ROOM_RANGE") }
    )
    NavIconButton(
      label = "ALL (14)",
      icon = Icons.Default.Menu,
      isSelected = false,
      onClick = onOpenMore
    )
  }
}

@Composable
private fun NavIconButton(
  label: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable { onClick() }
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (isSelected) Color(0x33FF2A3C) else Color.Transparent)
        .padding(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isSelected) SignalRed else Color(0xFF71717A),
        modifier = Modifier.size(20.dp)
      )
    }
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 9.sp,
      color = if (isSelected) SignalRed else Color(0xFF71717A)
    )
  }
}

