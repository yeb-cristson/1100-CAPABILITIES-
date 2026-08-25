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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AcousticEngine
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
fun AcousticScreen(
  engine: AcousticEngine,
  modifier: Modifier = Modifier
) {
  val spectrum by engine.spectrum.collectAsState()
  val isRecording by engine.isRecording.collectAsState()
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
              text = "M7 // ACOUSTIC FFT SPECTROGRAM",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "44.1 KHZ PCM MIC SAMPLING + ULTRASONIC BEACON",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Button(
            onClick = {
              if (isRecording) engine.stop() else engine.start()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isRecording) ThreatCritical else SignalRed,
              contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(
              imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isRecording) "STOP" else "SAMPLE",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
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

    // FFT Spectrogram Visualization Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkSurface, RoundedCornerShape(8.dp))
          .border(
            width = 1.5.dp,
            color = if (spectrum.isUltrasonicActive) ThreatCritical else DarkCardBorder,
            shape = RoundedCornerShape(8.dp)
          )
          .padding(14.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "PEAK: ${spectrum.peakHz} Hz",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = if (spectrum.isUltrasonicActive) ThreatCritical else RadarCyan
            )

            if (spectrum.isUltrasonicActive) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ThreatCritical, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "ULTRASONIC TONE DETECTED",
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp,
                  color = ThreatCritical
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 64 Frequency Bins Bars
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp)
              .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
              .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom
          ) {
            spectrum.frequencyBins.forEachIndexed { index, mag ->
              val isUltrasonicBin = index >= 48
              val barH = (mag * 120.dp.value).coerceIn(4f, 130f).dp
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(barH)
                  .clip(RoundedCornerShape(1.dp))
                  .background(if (isUltrasonicBin && mag > 0.04f) ThreatCritical else if (isUltrasonicBin) AlertAmber else RadarCyan)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("0 Hz (Bass)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextMuted)
            Text("10 kHz (Voice/Mid)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextMuted)
            Text("18-22 kHz (Ultrasonic)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = if (spectrum.isUltrasonicActive) ThreatCritical else AlertAmber)
          }
        }
      }
    }
  }
}
