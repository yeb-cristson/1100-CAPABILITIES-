package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.core.model.EmitterLocation
import com.example.engine.LocalizeEngine
import com.example.ui.components.RssiSignalBar
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LocalizeScreen(
  engine: LocalizeEngine,
  modifier: Modifier = Modifier
) {
  val heading by engine.headingDeg.collectAsState()
  val emitters by engine.emitters.collectAsState()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkSurface, RoundedCornerShape(8.dp))
          .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
          .padding(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "M5 // SPATIAL BEARING RADAR",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "ROTATION VECTOR GYRO + POLAR EMITTER PLOT",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Text(
            text = "HDG: %03d°".format(heading.toInt()),
            color = RadarCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }
    }

    // Polar Radar Canvas
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkSurface, RoundedCornerShape(12.dp))
          .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
          .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        PolarRadarPlot(emitters = emitters, heading = heading)
      }
    }

    if (emitters.isNotEmpty()) {
      item {
        Text(
          text = "TRACKED EMITTER BEARINGS (${emitters.size})",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          color = TextMuted
        )
      }

      items(emitters, key = { it.id }) { emitter ->
        EmitterBearingCard(emitter = emitter)
      }
    }
  }
}

@Composable
private fun PolarRadarPlot(
  emitters: List<EmitterLocation>,
  heading: Float,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .size(240.dp)
      .clip(CircleShape)
      .background(DarkSurfaceVariant)
      .border(1.dp, RadarCyan.copy(alpha = 0.3f), CircleShape),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val radius = size.minDimension / 2f - 6.dp.toPx()

      // Concentric Distance Rings (5m, 10m, 15m)
      drawCircle(RadarCyan.copy(alpha = 0.15f), radius * 0.95f, center, style = Stroke(1.dp.toPx()))
      drawCircle(RadarCyan.copy(alpha = 0.20f), radius * 0.65f, center, style = Stroke(1.dp.toPx()))
      drawCircle(RadarCyan.copy(alpha = 0.25f), radius * 0.35f, center, style = Stroke(1.dp.toPx()))

      // Heading indicator line (North / Front)
      drawLine(
        color = SignalRed,
        start = center,
        end = Offset(center.x, center.y - radius),
        strokeWidth = 2.dp.toPx()
      )

      // Emitter points
      emitters.forEach { em ->
        val px = center.x + em.relX * radius
        val py = center.y + em.relY * radius
        drawCircle(
          color = if (em.rssi > -65) SignalRed else RadarCyan,
          radius = 5.dp.toPx(),
          center = Offset(px, py)
        )
      }
    }

    Text(
      text = "N",
      color = SignalRed,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 10.sp,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
    )
  }
}

@Composable
private fun EmitterBearingCard(emitter: EmitterLocation) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(DarkSurface, RoundedCornerShape(6.dp))
      .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
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
          color = TextPrimary,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        )
        Text(
          text = "BEARING: %03d° | DIST: %.1f m est.".format(emitter.azimuthDeg.toInt(), emitter.estimatedDistanceM),
          color = RadarCyan,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      }

      RssiSignalBar(rssi = emitter.rssi)
    }
  }
}
