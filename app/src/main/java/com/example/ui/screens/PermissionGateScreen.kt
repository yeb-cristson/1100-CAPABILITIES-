package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed

@Composable
fun PermissionGateScreen(
  onRequestPermissions: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF050505))
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF0A0C12))
        .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
        .padding(24.dp)
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(28.dp))
          .background(Color(0x2200E5FF)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = RadarCyan,
          modifier = Modifier.size(32.dp)
        )
      }

      Text(
        text = "SENSOR ARRAY AUTHORIZATION",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
        letterSpacing = 1.sp,
        color = Color.White
      )

      Text(
        text = "RED EYE operates an aggressive passive hardware scanning suite requiring native access to:\n\n• BLE & Wi-Fi Airspace (Nearby Devices & Location)\n• Optical IR Retroreflection Camera\n• Ultrasonic Audio Spectrum Analysis (Microphone)",
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        color = Color(0xFFA1A1AA),
        lineHeight = 18.sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = onRequestPermissions,
        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = Color(0xFF090A0E)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "ENGAGE SENSOR SUITE",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = Color(0xFF090A0E)
        )
      }
    }
  }
}
