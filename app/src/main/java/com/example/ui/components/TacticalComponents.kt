package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.model.ThreatAlert
import com.example.core.model.ThreatLevel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import com.example.ui.theme.SignalRedContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThreatCritical
import kotlin.math.cos
import kotlin.math.sin

enum class NavDestination(val label: String, val title: String, val icon: ImageVector) {
  FUSION("FUSION", "M0 // FUSION RADAR", Icons.Default.Radar),
  AIRSPACE("AIRSPACE", "M1 // AIRSPACE RADAR", Icons.Default.Wifi),
  SUBNET("SUBNET", "M2 // SUBNET HUNTER", Icons.Default.Hub),
  EM_SWEEP("EM FLUX", "M3 // EM SWEEPER", Icons.Default.Sensors),
  PROTO("PROTO", "M4 // PROTO DISCOVERY", Icons.Default.Visibility),
  LOCALIZE("BEARING", "M5 // SPATIAL BEARING", Icons.Default.CompassCalibration),
  IR_GLINT("OPTICS", "M6 // IR GLINT CAM", Icons.Default.CameraAlt),
  ACOUSTIC("AUDIO", "M7 // ACOUSTIC FFT", Icons.Default.GraphicEq),
  TRACKERS("BEACONS", "M8 // BLE TRACKERS", Icons.Default.GpsFixed),
  EVIDENCE("VAULT", "M9 // EVIDENCE LOGS", Icons.Default.Fingerprint)
}

@Composable
fun TacticalHeader(
  threatLevel: ThreatLevel = ThreatLevel.NOMINAL,
  rfCount: Int = 0,
  subnetLabel: String = "",
  title: String? = null,
  subtitle: String? = null,
  rfDensity: String? = null,
  subnet: String? = null,
  onMenuClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isCritical = threatLevel == ThreatLevel.CRITICAL || (subnet != null && (subnet.contains("ALERT") || subnet.contains("THREAT") || subnet.contains("ACTIVE") || subnet.contains("CANDIDATE")))
  val displayTitle = title ?: "1100 CAPABILITIES"
  val displaySubtitle = subtitle ?: "RECON ARRAY // REAL SENSORS"
  val displayRf = rfDensity ?: "$rfCount EMITTERS"
  val displaySubnet = subnet ?: subnetLabel

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(DarkSurface)
      .border(1.dp, if (isCritical) ThreatCritical.copy(alpha = 0.5f) else DarkCardBorder)
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        IconButton(
          onClick = onMenuClick,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Tactical Modules",
            tint = TextPrimary
          )
        }

        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isCritical) SignalRed else RadarCyan)
            )
            Text(
              text = displayTitle,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Black,
              fontSize = 13.sp,
              letterSpacing = 1.sp,
              color = TextPrimary
            )
          }
          Text(
            text = displaySubtitle,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = TextMuted
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = displayRf,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = RadarCyan
        )
        Text(
          text = displaySubnet,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = TextMuted
        )
      }
    }
  }
}

@Composable
fun TacticalRadarView(
  threatCount: Int,
  threatLevel: ThreatLevel,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "RadarRotation"
  )

  val isCritical = threatLevel == ThreatLevel.CRITICAL

  Box(
    modifier = modifier
      .size(240.dp)
      .clip(CircleShape)
      .background(DarkSurfaceVariant)
      .border(
        width = 1.5.dp,
        color = if (isCritical) SignalRed.copy(alpha = 0.8f) else RadarCyan.copy(alpha = 0.4f),
        shape = CircleShape
      )
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val radius = size.minDimension / 2f - 6.dp.toPx()

      // Concentric Rings
      drawCircle(
        color = RadarCyan.copy(alpha = 0.15f),
        radius = radius * 0.95f,
        center = center,
        style = Stroke(width = 1.dp.toPx())
      )
      drawCircle(
        color = RadarCyan.copy(alpha = 0.20f),
        radius = radius * 0.65f,
        center = center,
        style = Stroke(width = 1.dp.toPx())
      )
      drawCircle(
        color = RadarCyan.copy(alpha = 0.25f),
        radius = radius * 0.35f,
        center = center,
        style = Stroke(width = 1.dp.toPx())
      )

      // Crosshairs
      drawLine(
        color = RadarCyan.copy(alpha = 0.20f),
        start = Offset(0f, center.y),
        end = Offset(size.width, center.y),
        strokeWidth = 1.dp.toPx()
      )
      drawLine(
        color = RadarCyan.copy(alpha = 0.20f),
        start = Offset(center.x, 0f),
        end = Offset(center.x, size.height),
        strokeWidth = 1.dp.toPx()
      )

      // Sweep Beam
      val rad = Math.toRadians(rotation.toDouble()).toFloat()
      val beamEnd = Offset(
        x = center.x + radius * cos(rad),
        y = center.y + radius * sin(rad)
      )
      drawLine(
        color = if (isCritical) SignalRed else RadarCyan,
        start = center,
        end = beamEnd,
        strokeWidth = 2.dp.toPx()
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .size(92.dp)
        .clip(CircleShape)
        .background(if (isCritical) SignalRedContainer else DarkSurface)
        .border(
          width = 1.5.dp,
          color = if (isCritical) SignalRed else RadarCyan,
          shape = CircleShape
        )
    ) {
      Icon(
        imageVector = if (isCritical) Icons.Default.Warning else Icons.Default.Security,
        contentDescription = "Threat Status",
        tint = if (isCritical) SignalRed else RadarCyan,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = if (isCritical) "$threatCount ALERTS" else "NOMINAL",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = if (isCritical) SignalRed else TextPrimary
      )
      Text(
        text = "ZERO SIMULATION",
        fontFamily = FontFamily.Monospace,
        fontSize = 7.sp,
        color = TextMuted
      )
    }
  }
}

@Composable
fun TacticalRadarHUD(
  threatCount: Int,
  threatLevel: ThreatLevel,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  TacticalRadarView(
    threatCount = threatCount,
    threatLevel = threatLevel,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun TacticalTerminal(
  logs: List<String>,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(DarkSurface)
      .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
      .padding(12.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "TACTICAL TELEMETRY FEED",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = TextMuted
        )
        Text(
          text = "OFFLINE LAN READY",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = RadarCyan
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      logs.take(5).forEach { log ->
        val isCrit = log.contains("CRITICAL") || log.contains("ALERT") || log.contains("DETECTED")
        Text(
          text = log,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = if (isCrit) ThreatCritical else TextSecondary,
          lineHeight = 14.sp
        )
      }
    }
  }
}

@Composable
fun ThreatCard(
  alert: ThreatAlert,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(DarkSurface, RoundedCornerShape(8.dp))
      .border(1.dp, ThreatCritical, RoundedCornerShape(8.dp))
      .padding(12.dp)
  ) {
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = "Alert",
        tint = SignalRed,
        modifier = Modifier.size(22.dp)
      )

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = alert.title,
            color = SignalRed,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
          Text(
            text = alert.sourceModule,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = alert.details,
          color = TextSecondary,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp
        )
      }
    }
  }
}

@Composable
fun RssiSignalBar(
  rssi: Int,
  modifier: Modifier = Modifier
) {
  val bars = when {
    rssi > -60 -> 4
    rssi > -70 -> 3
    rssi > -80 -> 2
    rssi > -90 -> 1
    else -> 0
  }

  val barColor = when {
    bars >= 3 -> RadarCyan
    bars == 2 -> AlertAmber
    else -> SignalRed
  }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    (1..4).forEach { index ->
      val isActive = index <= bars
      Box(
        modifier = Modifier
          .width(3.dp)
          .height((index * 3.5).dp)
          .clip(RoundedCornerShape(1.dp))
          .background(if (isActive) barColor else Color(0x22FFFFFF))
      )
    }
    Spacer(modifier = Modifier.width(3.dp))
    Text(
      text = "$rssi dBm",
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = barColor
    )
  }
}

@Composable
fun SignalQualityMeter(
  rssi: Int,
  modifier: Modifier = Modifier
) {
  RssiSignalBar(rssi = rssi, modifier = modifier)
}

@Composable
fun TacticalNavBar(
  currentDestination: NavDestination,
  onNavigate: (NavDestination) -> Unit,
  modifier: Modifier = Modifier
) {
  val primaryTabs = listOf(
    NavDestination.FUSION,
    NavDestination.AIRSPACE,
    NavDestination.SUBNET,
    NavDestination.EM_SWEEP,
    NavDestination.EVIDENCE
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .background(DarkSurface)
      .border(width = 1.dp, color = DarkCardBorder)
      .padding(horizontal = 4.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically
  ) {
    primaryTabs.forEach { tab ->
      val isSelected = currentDestination == tab
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable { onNavigate(tab) }
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) SignalRedContainer else Color.Transparent)
            .padding(4.dp)
        ) {
          Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (isSelected) SignalRed else TextMuted,
            modifier = Modifier.size(20.dp)
          )
        }
        Text(
          text = tab.label,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 9.sp,
          color = if (isSelected) SignalRed else TextMuted
        )
      }
    }
  }
}

@Composable
fun ModuleDrawerSheet(
  currentDestination: NavDestination,
  onSelect: (NavDestination) -> Unit,
  onClose: () -> Unit
) {
  ModalDrawerSheet(
    drawerContainerColor = DarkSurface,
    drawerContentColor = TextPrimary,
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
        modifier = Modifier.padding(bottom = 14.dp)
      ) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SignalRed)
        )
        Text(
          text = "1100 CAPABILITIES // 10 MODULES",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = TextPrimary
        )
      }

      HorizontalDivider(color = DarkCardBorder)
      Spacer(modifier = Modifier.height(8.dp))

      NavDestination.values().forEach { dest ->
        val isSelected = currentDestination == dest
        NavigationDrawerItem(
          icon = {
            Icon(
              imageVector = dest.icon,
              contentDescription = null,
              tint = if (isSelected) SignalRed else TextMuted
            )
          },
          label = {
            Text(
              text = dest.title,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) TextPrimary else TextSecondary
            )
          },
          selected = isSelected,
          onClick = {
            onSelect(dest)
            onClose()
          },
          colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = SignalRedContainer,
            unselectedContainerColor = Color.Transparent
          ),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.padding(vertical = 2.dp)
        )
      }
    }
  }
}

@Composable
fun PermissionGate(
  onAllPermissionsGranted: () -> Unit,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current

  val requiredPermissions = remember {
    val list = mutableListOf(
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      list.add(Manifest.permission.BLUETOOTH_SCAN)
      list.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    list.toTypedArray()
  }

  var allGranted by remember {
    mutableStateOf(
      requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
      }
    )
  }

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    val fineLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    if (fineLocation) {
      allGranted = true
      onAllPermissionsGranted()
    }
  }

  LaunchedEffect(Unit) {
    if (!allGranted) {
      launcher.launch(requiredPermissions)
    } else {
      onAllPermissionsGranted()
    }
  }

  if (allGranted) {
    content()
  } else {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkSurface, RoundedCornerShape(12.dp))
          .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
          .padding(24.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = SignalRed,
          modifier = Modifier.size(48.dp)
        )

        Text(
          text = "1100 CAPABILITIES",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextPrimary
        )

        Text(
          text = "Physical Reconnaissance & Counter-Surveillance Suite",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = RadarCyan
        )

        Text(
          text = "To access hardware sensors (Bluetooth BLE, Wi-Fi RSSI, Camera IR glint detection, Magnetometer EM flux, Microphone FFT, and on-device offline persistence), please grant sensor permissions.",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = TextSecondary,
          lineHeight = 16.sp
        )

        Button(
          onClick = { launcher.launch(requiredPermissions) },
          colors = ButtonDefaults.buttonColors(
            containerColor = SignalRed,
            contentColor = TextPrimary
          ),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "ENGAGE HARDWARE HOOKS",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }
    }
  }
}
