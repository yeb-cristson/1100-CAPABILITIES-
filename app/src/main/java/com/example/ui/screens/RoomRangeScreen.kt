package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ProximityZone
import com.example.engine.RoomDeviceProfile
import com.example.engine.RoomProximityEngine
import com.example.ui.components.RssiSignalBar
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RoomRangeScreen(
  engine: RoomProximityEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val devices by engine.devices.collectAsState()
  val summary by engine.summary.collectAsState()
  val selectedRange by engine.selectedRangeLimit.collectAsState()
  var selectedTab by remember { mutableStateOf("ALL") } // ALL, IN_ROOM, SUSPECT

  val filteredDevices = remember(devices, selectedTab, selectedRange) {
    devices.filter { d ->
      val tabMatch = when (selectedTab) {
        "IN_ROOM" -> d.zone == ProximityZone.IMMEDIATE || d.zone == ProximityZone.SAME_ROOM
        "SUSPECT" -> d.isSuspectOrCamera
        else -> true
      }
      tabMatch && d.distanceMeters <= selectedRange
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
  ) {
    item {
      TacticalHeader(
        threatLevel = summary.threatLevel,
        title = "M10: ROOM & RANGE RADAR",
        subtitle = "PHYSICAL DEVICE PROXIMITY ESTIMATION",
        rfDensity = "${summary.inRoomCount} IN-ROOM",
        subnet = "DENSITY: ${summary.estimatedRoomRfDensity}",
        onMenuClick = onOpenDrawer
      )
    }

    // Top Physical Proximity Metrics Matrix
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        RoomMetricCard(
          label = "IN-ROOM (<4.5m)",
          count = "${summary.inRoomCount}",
          color = Color(0xFFFF9800),
          modifier = Modifier.weight(1f)
        )
        RoomMetricCard(
          label = "IMMEDIATE (<1.5m)",
          count = "${summary.immediateCount}",
          color = SignalRed,
          modifier = Modifier.weight(1f)
        )
        RoomMetricCard(
          label = "PERIMETER (<10m)",
          count = "${summary.perimeterCount}",
          color = RadarCyan,
          modifier = Modifier.weight(1f)
        )
        RoomMetricCard(
          label = "SUSPECT / CAM",
          count = "${summary.suspectCount}",
          color = if (summary.suspectCount > 0) SignalRed else TextMuted,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Radar Concentric Proximity Display
    item {
      RoomRadarDisplay(
        devices = filteredDevices,
        maxRangeMeters = selectedRange
      )
    }

    // Interactive Distance Range Slider & Filter Selector
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
          .padding(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "SCAN RANGE BOUNDARY:",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = "≤ ${selectedRange}m (${if (selectedRange <= 1.5f) "Personal" else if (selectedRange <= 4.5f) "Room" else "Perimeter"})",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = RadarCyan
          )
        }

        Slider(
          value = selectedRange,
          onValueChange = { engine.setRangeFilter(it) },
          valueRange = 1.0f..30.0f,
          steps = 28,
          colors = SliderDefaults.colors(
            thumbColor = RadarCyan,
            activeTrackColor = RadarCyan,
            inactiveTrackColor = Color(0x3300E5FF)
          )
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          RangePresetButton("1.5m (Touch)", selectedRange == 1.5f) { engine.setRangeFilter(1.5f) }
          RangePresetButton("4.5m (Room)", selectedRange == 4.5f) { engine.setRangeFilter(4.5f) }
          RangePresetButton("10m (Zone)", selectedRange == 10.0f) { engine.setRangeFilter(10.0f) }
          RangePresetButton("30m (Max)", selectedRange == 30.0f) { engine.setRangeFilter(30.0f) }
        }
      }
    }

    // Filter Tabs
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterTabButton("ALL (${devices.size})", selectedTab == "ALL") { selectedTab = "ALL" }
        FilterTabButton("IN-ROOM (${summary.inRoomCount})", selectedTab == "IN_ROOM") { selectedTab = "IN_ROOM" }
        FilterTabButton("SUSPECT (${summary.suspectCount})", selectedTab == "SUSPECT") { selectedTab = "SUSPECT" }
      }
    }

    // Detected Devices List
    if (filteredDevices.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "NO EMITTERS DETECTED WITHIN SELECTED RANGE",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextMuted
          )
        }
      }
    } else {
      items(filteredDevices, key = { it.id }) { device ->
        RoomDeviceItemCard(device = device)
      }
    }
  }
}

@Composable
private fun RoomMetricCard(
  label: String,
  count: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = count,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Black,
      fontSize = 16.sp,
      color = color
    )
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 8.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
      maxLines = 1
    )
  }
}

@Composable
private fun RoomRadarDisplay(
  devices: List<RoomDeviceProfile>,
  maxRangeMeters: Float
) {
  val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
  val angle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "sweep_angle"
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(260.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF070B0E))
      .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = (size.minDimension / 2f) - 20f

      // Concentric Distance Rings: 1.5m, 4.5m, 10m
      val ringFractions = listOf(0.2f to "1.5m", 0.5f to "4.5m", 0.85f to "10m")
      ringFractions.forEach { (fraction, label) ->
        val radius = maxRadius * fraction
        drawCircle(
          color = Color(0x2200E5FF),
          radius = radius,
          center = center,
          style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        )
      }

      // Crosshairs
      drawLine(
        color = Color(0x2200E5FF),
        start = Offset(center.x, 20f),
        end = Offset(center.x, size.height - 20f),
        strokeWidth = 1f
      )
      drawLine(
        color = Color(0x2200E5FF),
        start = Offset(20f, center.y),
        end = Offset(size.width - 20f, center.y),
        strokeWidth = 1f
      )

      // Rotating Sweep line
      val sweepRad = Math.toRadians(angle.toDouble())
      val sweepEnd = Offset(
        (center.x + maxRadius * cos(sweepRad)).toFloat(),
        (center.y + maxRadius * sin(sweepRad)).toFloat()
      )
      drawLine(
        color = Color(0x6600E5FF),
        start = center,
        end = sweepEnd,
        strokeWidth = 2f
      )

      // Center user dot
      drawCircle(
        color = Color(0xFF00E5FF),
        radius = 5f,
        center = center
      )

      // Render Devices as radar blips based on distance
      devices.take(24).forEachIndexed { index, device ->
        val distFraction = (device.distanceMeters / maxRangeMeters).coerceIn(0.1f, 0.95f)
        val r = maxRadius * distFraction
        // Spread evenly by index hash
        val theta = Math.toRadians((index * 47.0 + 23.0) % 360.0)
        val devPos = Offset(
          (center.x + r * cos(theta)).toFloat(),
          (center.y + r * sin(theta)).toFloat()
        )

        val blipColor = if (device.isSuspectOrCamera) Color(0xFFFF2A2A)
        else if (device.zone == ProximityZone.IMMEDIATE) Color(0xFFFF5252)
        else if (device.zone == ProximityZone.SAME_ROOM) Color(0xFFFF9800)
        else Color(0xFF00E5FF)

        // Draw Ping ring and center
        drawCircle(
          color = blipColor.copy(alpha = 0.4f),
          radius = if (device.isSuspectOrCamera) 10f else 6f,
          center = devPos
        )
        drawCircle(
          color = blipColor,
          radius = if (device.isSuspectOrCamera) 5f else 3f,
          center = devPos
        )
      }
    }

    // Legend Overlays
    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      LegendItem("IMMEDIATE", Color(0xFFFF5252))
      LegendItem("SAME ROOM", Color(0xFFFF9800))
      LegendItem("PERIMETER", Color(0xFF00E5FF))
      LegendItem("SUSPECT", Color(0xFFFF2A2A))
    }
  }
}

@Composable
private fun LegendItem(label: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .clip(CircleShape)
        .background(color)
    )
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 8.sp,
      color = Color(0xFFA1A1AA)
    )
  }
}

@Composable
private fun RoomDeviceItemCard(device: RoomDeviceProfile) {
  val zoneColor = Color(device.zone.colorHex)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(DarkSurface)
      .border(
        width = 1.dp,
        color = if (device.isSuspectOrCamera) SignalRed else DarkCardBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .padding(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(zoneColor)
        )
        Text(
          text = device.displayName,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = Color.White,
          maxLines = 1
        )
      }

      Text(
        text = "≈ ${device.distanceMeters}m",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        color = zoneColor
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${device.medium} // ${device.macOrIp}",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = TextMuted
      )
      Text(
        text = "${device.zone.label} (${device.confidencePercent}% conf)",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = zoneColor
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "VENDOR: ${device.vendor}",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = Color(0xFFA1A1AA)
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x3300E5FF))
            .border(0.5.dp, RadarCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
          Text(
            text = "AI: ${device.aiTag.uppercase()}",
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = RadarCyan
          )
        }
        RssiSignalBar(rssi = device.rssi)
      }
    }
  }
}

@Composable
private fun RangePresetButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) RadarCyan else Color(0x1AFFFFFF))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) Color.Black else Color.White
    )
  }
}

@Composable
private fun RowScope.FilterTabButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .weight(1f)
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) Color(0x3300E5FF) else DarkSurface)
      .border(1.dp, if (isSelected) RadarCyan else DarkCardBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) RadarCyan else Color(0xFFA1A1AA)
    )
  }
}
