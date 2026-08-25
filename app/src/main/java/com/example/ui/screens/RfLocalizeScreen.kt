package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.RfLocalizeEngine
import com.example.core.model.EmitterLocation
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RfLocalizeScreen(
  localizeEngine: RfLocalizeEngine,
  onOpenDrawer: () -> Unit
) {
  val heading by localizeEngine.azimuthHeading.collectAsState()
  val stepCount by localizeEngine.stepCount.collectAsState()
  val localizedEmitters by localizeEngine.localizedEmitters.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF050505))
  ) {
    TacticalHeader(
      title = "RF LOCALIZATION",
      subtitle = "BEARING TRIANGULATION // 2D POLAR RADAR",
      rfDensity = "${localizedEmitters.size} TARGETS",
      subnet = "HEADING %03d°".format(heading.toInt()),
      onMenuClick = onOpenDrawer
    )

    // Heading and Step Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0A0A0A))
        .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CompassCalibration, contentDescription = null, tint = RadarCyan, modifier = Modifier.size(16.dp))
        Text(
          text = "HEADING: %03d° MAG".format(heading.toInt()),
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
        Text(
          text = "STEPS: $stepCount",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = Color(0xFF71717A)
        )
      }

      IconButton(
        onClick = { localizeEngine.resetOrigin() },
        modifier = Modifier.size(28.dp)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = "Reset Origin", tint = RadarCyan, modifier = Modifier.size(16.dp))
      }
    }

    // 2D Polar Localization Canvas
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(260.dp)
        .padding(14.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF090A0E))
        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp)),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f - 16.dp.toPx()

        // 3 Range Circles (5m, 10m, 15m)
        drawCircle(color = Color(0x1F00E5FF), radius = maxRadius, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(color = Color(0x1F00E5FF), radius = maxRadius * 0.66f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(color = Color(0x1F00E5FF), radius = maxRadius * 0.33f, center = center, style = Stroke(1.dp.toPx()))

        // Crosshairs
        drawLine(Color(0x1AFFFFFF), Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
        drawLine(Color(0x1AFFFFFF), Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())

        // Heading Direction Needle
        val headingRad = Math.toRadians((heading - 90).toDouble()).toFloat()
        val needleEnd = Offset(
          center.x + (maxRadius + 6.dp.toPx()) * cos(headingRad),
          center.y + (maxRadius + 6.dp.toPx()) * sin(headingRad)
        )
        drawLine(RadarCyan, center, needleEnd, 2.dp.toPx())

        // Plot Emitters
        localizedEmitters.forEach { emitter ->
          val normDist = (emitter.estimatedDistanceM / 15f).coerceIn(0.1f, 1.0f)
          val rad = Math.toRadians((emitter.azimuthDeg - 90).toDouble()).toFloat()
          val ex = center.x + (normDist * maxRadius) * cos(rad)
          val ey = center.y + (normDist * maxRadius) * sin(rad)

          val color = if (emitter.rssi > -65) SignalRed else AlertAmber
          drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(ex, ey))
          drawCircle(color = color.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(ex, ey))
        }
      }

      // Center Observer Icon
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(Color.White)
      )
    }

    // Emitters List
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(localizedEmitters, key = { it.id }) { emitter ->
        LocalizedEmitterItem(emitter = emitter)
      }

      if (localizedEmitters.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(100.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "NO RF EMITTERS LOCALIZED IN VICINITY",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = Color(0xFF52525B)
            )
          }
        }
      }
    }
  }
}

@Composable
fun LocalizedEmitterItem(emitter: EmitterLocation) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF0C0E14))
      .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
      .padding(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = emitter.name,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = Color.White
        )
        Text(
          text = "BEARING: %03d° // EST DIST: %.1fm".format(emitter.azimuthDeg.toInt(), emitter.estimatedDistanceM),
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = RadarCyan
        )
      }

      Text(
        text = "${emitter.rssi} dBm",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = if (emitter.rssi > -65) SignalRed else AlertAmber
      )
    }
  }
}
