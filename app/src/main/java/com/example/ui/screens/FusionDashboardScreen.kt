package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.hub.ReconHub
import com.example.core.model.ThreatLevel
import com.example.engine.AiTaggingState
import com.example.ui.components.TacticalRadarView
import com.example.ui.components.TacticalTerminal
import com.example.ui.components.ThreatCard
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThreatCritical

@Composable
fun FusionDashboardScreen(
  hub: ReconHub,
  onOpenGlint: () -> Unit = {},
  onNavigateToModule: (String) -> Unit = {},
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val fusionState by hub.fusionState.collectAsState()
  val terminalFeed by hub.terminalLogs.collectAsState()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Interactive Tactical Radar HUD
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkSurface, RoundedCornerShape(12.dp))
          .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
          .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        TacticalRadarView(
          threatCount = fusionState.activeAlerts.size,
          threatLevel = fusionState.threatLevel,
          onClick = onOpenGlint
        )
      }
    }

    // 2. Active Threat Matrix / Alerts
    if (fusionState.activeAlerts.isNotEmpty()) {
      item {
        Text(
          text = "ACTIVE THREAT MATRIX (${fusionState.activeAlerts.size})",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          color = SignalRed
        )
      }
      items(fusionState.activeAlerts, key = { it.id }) { alert ->
        ThreatCard(alert = alert)
      }
    }

    // 3. Multi-Sensor Telemetry Grid
    item {
      Text(
        text = "SENSOR STATUS SUMMARY",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = TextMuted
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SensorStatCard(
          title = "RF AIRSPACE",
          value = "${fusionState.rfDeviceCount}",
          unit = "emitters",
          icon = Icons.Default.Wifi,
          status = if (fusionState.rfDeviceCount > 0) "ACTIVE" else "SCANNING",
          modifier = Modifier.weight(1f)
        )
        SensorStatCard(
          title = "BLE TRACKERS",
          value = "${fusionState.bleTrackerCount}",
          unit = "beacons",
          icon = Icons.Default.GpsFixed,
          status = if (fusionState.bleTrackerCount > 0) "OBSERVED" else "CLEAR",
          modifier = Modifier.weight(1f)
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SensorStatCard(
          title = "RTSP CAMERAS",
          value = "${fusionState.rtspCameraCount}",
          unit = "streams",
          icon = Icons.Default.Videocam,
          status = if (fusionState.rtspCameraCount > 0) "ALERT" else "CLEAN",
          highlight = fusionState.rtspCameraCount > 0,
          modifier = Modifier.weight(1f)
        )
        SensorStatCard(
          title = "EM FLUX (MAG)",
          value = "%.1f".format(fusionState.emMagnitudeUt),
          unit = "uT",
          icon = Icons.Default.Sensors,
          status = if (fusionState.emSpikeActive) "SPIKE" else "NORMAL",
          highlight = fusionState.emSpikeActive,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // 4. Automated AI Classification HUD
    item {
      val aiState by (hub.repository.aiState?.collectAsState() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AiTaggingState()) })
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
          .clickable { onNavigateToModule("RECON_VAULT") }
          .padding(12.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = RadarCyan, modifier = Modifier.size(16.dp))
              Text(
                text = "AI INFERENCE ENGINE // ROOM DB TAGGER",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White
              )
            }
            Text(
              text = "VIEW VAULT >",
              fontFamily = FontFamily.Monospace,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = RadarCyan
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            AiTagBadge("CAMERAS", "${aiState.cameraCount}", SignalRed, Modifier.weight(1f))
            AiTagBadge("PHONES", "${aiState.smartphoneCount}", RadarCyan, Modifier.weight(1f))
            AiTagBadge("IOT", "${aiState.iotCount}", Color(0xFFFF9800), Modifier.weight(1f))
            AiTagBadge("PCS", "${aiState.laptopCount}", Color(0xFF7C4DFF), Modifier.weight(1f))
            AiTagBadge("TAGS", "${aiState.trackerCount}", Color(0xFFFF4081), Modifier.weight(1f))
          }
        }
      }
    }

    // 5. Quick Access Tactical Matrix for Advanced Capabilities
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          TacticalActionCard(
            title = "D3 HEATMAP",
            subtitle = "Spatial Density",
            icon = Icons.Default.Language,
            accentColor = Color(0xFF00E5FF),
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("HEATMAP") }
          )
          TacticalActionCard(
            title = "ROOM RANGE",
            subtitle = "Physical Radar",
            icon = Icons.Default.Explore,
            accentColor = RadarCyan,
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("ROOM_RANGE") }
          )
          TacticalActionCard(
            title = "KERNEL AUDIT",
            subtitle = "Linux Shell",
            icon = Icons.Default.Code,
            accentColor = SignalRed,
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("KERNEL_AUDIT") }
          )
          TacticalActionCard(
            title = "AI VAULT",
            subtitle = "Auto Tagging",
            icon = Icons.Default.Folder,
            accentColor = Color(0xFF00E676),
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("RECON_VAULT") }
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          TacticalActionCard(
            title = "RAW PACKETS",
            subtitle = "Header Sniffer",
            icon = Icons.Default.GraphicEq,
            accentColor = RadarCyan,
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("PACKET_SNIFFER") }
          )
          TacticalActionCard(
            title = "BT COMMS",
            subtitle = "Mesh Link",
            icon = Icons.Default.Share,
            accentColor = Color(0xFF7C4DFF),
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("BT_COMMS") }
          )
          TacticalActionCard(
            title = "HOST SERVER",
            subtitle = "LAN Broadcast",
            icon = Icons.Default.Storage,
            accentColor = Color(0xFFFF9800),
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToModule("TACTICAL_SERVER") }
          )
        }
      }
    }

    // 5. Live Terminal Telemetry Feed
    item {
      TacticalTerminal(logs = terminalFeed)
    }
  }
}

@Composable
private fun TacticalActionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(DarkSurface)
      .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(10.dp)
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = accentColor,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = title,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        color = Color.White,
        maxLines = 1
      )
      Text(
        text = subtitle,
        fontFamily = FontFamily.Monospace,
        fontSize = 7.5.sp,
        color = TextMuted,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun SensorStatCard(
  title: String,
  value: String,
  unit: String,
  icon: ImageVector,
  status: String,
  highlight: Boolean = false,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(DarkSurface)
      .border(
        width = 1.dp,
        color = if (highlight) ThreatCritical else DarkCardBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .padding(10.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = TextMuted
        )
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (highlight) SignalRed else RadarCyan,
          modifier = Modifier.size(14.dp)
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = value,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = if (highlight) ThreatCritical else TextPrimary
        )
        Text(
          text = unit,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = TextMuted,
          modifier = Modifier.padding(bottom = 2.dp)
        )
      }

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = status,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = if (highlight) ThreatCritical else AlertAmber
      )
    }
  }
}

@Composable
private fun AiTagBadge(
  label: String,
  count: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(4.dp))
      .background(Color(0xFF090D12))
      .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = count,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Black,
      fontSize = 12.sp,
      color = color
    )
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 7.sp,
      color = TextMuted
    )
  }
}

