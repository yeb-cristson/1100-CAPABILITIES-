package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.EmEngine
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
import com.example.ui.theme.ThreatCritical

@Composable
fun EmSweeperScreen(
  engine: EmEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val reading by engine.reading.collectAsState()
  val isMonitoring by engine.isMonitoring.collectAsState()
  val statusMessage by engine.statusMessage.collectAsState()

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
              text = "M3 // EM FLUX SWEEPER",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "MAGNETOMETER 60HZ HARDWARE SENSOR",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              onClick = {
                if (isMonitoring) engine.stop() else engine.start()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isMonitoring) ThreatCritical else SignalRed,
                contentColor = TextPrimary
              ),
              shape = RoundedCornerShape(6.dp)
            ) {
              Icon(
                imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isMonitoring) "STOP" else "SWEEP",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }

            IconButton(onClick = { engine.resetBaseline() }) {
              Icon(Icons.Default.Refresh, contentDescription = "Calibrate", tint = TextMuted)
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = statusMessage,
          color = TextSecondary,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp
        )
      }
    }

    // Live Meter Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkSurface, RoundedCornerShape(8.dp))
          .border(
            width = 1.5.dp,
            color = if (reading.isSpike) ThreatCritical else DarkCardBorder,
            shape = RoundedCornerShape(8.dp)
          )
          .padding(16.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "TOTAL FLUX MAGNITUDE",
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              color = TextMuted
            )
            if (reading.isSpike) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ThreatCritical, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "ANOMALOUS FLUX SPIKE",
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp,
                  color = ThreatCritical
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "%.2f".format(reading.magnitudeUt),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Black,
              fontSize = 32.sp,
              color = if (reading.isSpike) ThreatCritical else RadarCyan
            )
            Text(
              text = "microTesla (uT)",
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = TextMuted,
              modifier = Modifier.padding(bottom = 6.dp)
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          val normalizedProgress = (reading.magnitudeUt / 120f).coerceIn(0f, 1f)
          LinearProgressIndicator(
            progress = { normalizedProgress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (reading.isSpike) ThreatCritical else RadarCyan,
            trackColor = DarkSurfaceVariant
          )

          Spacer(modifier = Modifier.height(12.dp))

          // 3-Axis Telemetry
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("X: %.1f uT".format(reading.x), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            Text("Y: %.1f uT".format(reading.y), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            Text("Z: %.1f uT".format(reading.z), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("BASELINE EMA: %.1f uT".format(reading.baselineUt), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
            Text("DELTA: %+.1f uT".format(reading.deltaUt), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (reading.isSpike) ThreatCritical else AlertAmber)
          }
        }
      }
    }
  }
}
