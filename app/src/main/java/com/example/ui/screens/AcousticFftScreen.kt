package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.engine.AcousticFftEngine
import com.example.core.hub.ReconHub
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed

@Composable
fun AcousticFftScreen(
  acousticEngine: AcousticFftEngine,
  onOpenDrawer: () -> Unit
) {
  val reconHub = ReconHub.getInstance()
  val spectrum by acousticEngine.spectrum.collectAsState()
  val isRecording by acousticEngine.isRecording.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF050505))
  ) {
    TacticalHeader(
      title = "ACOUSTIC FFT",
      subtitle = "ULTRASONIC BEACON // 18-22kHz WATERMARK",
      rfDensity = "${spectrum.peakHz} Hz PEAK",
      subnet = if (spectrum.isUltrasonicActive) "ULTRASONIC ACTIVE" else "NOMINAL AUDIO",
      onMenuClick = onOpenDrawer
    )

    // Audio Metrics Dashboard Box
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (spectrum.isUltrasonicActive) Color(0x3333090E) else Color(0xFF0C0E14))
        .border(1.dp, if (spectrum.isUltrasonicActive) SignalRed else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
        .padding(16.dp)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "DOMINANT FREQUENCY",
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              color = Color(0xFF71717A)
            )
            Text(
              text = "${spectrum.peakHz} Hz",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Black,
              fontSize = 26.sp,
              color = if (spectrum.isUltrasonicActive) SignalRed else RadarCyan
            )
          }

          Button(
            onClick = {
              if (isRecording) acousticEngine.stop() else acousticEngine.start(coroutineScope)
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isRecording) SignalRed else RadarCyan
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = null,
              tint = Color(0xFF090A0E),
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isRecording) "STOP" else "SAMPLE",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color(0xFF090A0E)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "ULTRASONIC ENERGY: %.2f".format(spectrum.ultrasonicEnergy),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (spectrum.isUltrasonicActive) SignalRed else Color(0xFF71717A)
          )
          Text(
            text = if (spectrum.isUltrasonicActive) "CROSS-DEVICE TRACKING DETECTED" else "BACKGROUND SPECTRUM CLEAN",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (spectrum.isUltrasonicActive) SignalRed else AlertAmber
          )
        }
      }
    }

    // 64-Bin FFT Visualizer Bar Graph
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 14.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF090A0E))
        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
        .padding(14.dp)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "64-BIN FFT (0 Hz - 22,050 Hz)",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Color(0xFF71717A)
          )
          Text(
            text = "18kHz+ ULTRASONIC ZONE",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = SignalRed
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(modifier = Modifier.fillMaxSize().weight(1f)) {
          val bins = spectrum.frequencyBins
          if (bins.isNotEmpty()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidth = totalWidth / bins.size
            val gap = 1.dp.toPx()

            bins.forEachIndexed { i, mag ->
              val isUltrasonicBin = i >= 40 // > 17 kHz
              val barH = (mag * totalHeight).coerceIn(2f, totalHeight)
              val x = i * barWidth
              val y = totalHeight - barH

              val color = when {
                isUltrasonicBin && mag > 0.3f -> SignalRed
                isUltrasonicBin -> SignalRed.copy(alpha = 0.5f)
                mag > 0.6f -> AlertAmber
                else -> RadarCyan
              }

              drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth - gap, barH)
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))
  }
}
