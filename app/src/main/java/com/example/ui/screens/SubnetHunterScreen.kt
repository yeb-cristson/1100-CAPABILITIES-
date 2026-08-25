package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.SubnetHost
import com.example.engine.SubnetEngine
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
fun SubnetHunterScreen(
  engine: SubnetEngine,
  onOpenDrawer: () -> Unit = {},
  onNavigateToPacketAnalyzer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val hosts by engine.hosts.collectAsState()
  val isScanning by engine.isScanning.collectAsState()
  val progress by engine.scanProgress.collectAsState()
  val subnet by engine.currentSubnet.collectAsState()
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
              text = "M2 // SUBNET HUNTER",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "IP SWEEPER & RTSP PORT 554 DETECTOR",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              onClick = {
                if (isScanning) engine.stopScan() else engine.startScan()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isScanning) ThreatCritical else SignalRed,
                contentColor = TextPrimary
              ),
              shape = RoundedCornerShape(6.dp)
            ) {
              Icon(
                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isScanning) "STOP" else "HUNT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }

            IconButton(onClick = { engine.clear() }) {
              Icon(Icons.Default.Refresh, contentDescription = "Clear", tint = TextMuted)
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Raw Socket Packet Sniffer Launch Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090E14), RoundedCornerShape(4.dp))
            .border(1.dp, RadarCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "RAW SOCKET HEADER INSPECTOR",
              fontFamily = FontFamily.Monospace,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = RadarCyan
            )
            Text(
              text = "Inspect local traffic flows & passive host TTL signatures",
              fontFamily = FontFamily.Monospace,
              fontSize = 7.5.sp,
              color = TextMuted
            )
          }

          Button(
            onClick = onNavigateToPacketAnalyzer,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0x3300E5FF),
              contentColor = RadarCyan
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "OPEN ANALYZER",
              fontFamily = FontFamily.Monospace,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isScanning) {
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = SignalRed,
            trackColor = DarkCardBorder
          )
          Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
          text = "SUBNET: ${subnet ?: "Offline"} | $statusMessage",
          color = TextSecondary,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp
        )
      }
    }

    if (hosts.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (isScanning) "Probing /24 subnet addresses for RTSP stream ports..." else "No active hosts detected. Connect to Wi-Fi/LAN and tap HUNT.",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
          )
        }
      }
    } else {
      items(hosts, key = { it.ip }) { host ->
        SubnetHostCard(host = host)
      }
    }
  }
}

@Composable
private fun SubnetHostCard(host: SubnetHost) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(DarkSurface, RoundedCornerShape(6.dp))
      .border(
        width = 1.dp,
        color = if (host.isRtspCamera) ThreatCritical else DarkCardBorder,
        shape = RoundedCornerShape(6.dp)
      )
      .padding(12.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (host.isRtspCamera) {
            Icon(Icons.Default.Videocam, contentDescription = null, tint = ThreatCritical, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(
            text = host.ip,
            color = if (host.isRtspCamera) ThreatCritical else TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }

        Text(
          text = "${host.latencyMs} ms",
          color = RadarCyan,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "PORTS: ${if (host.openPorts.isEmpty()) "Ping echo only" else host.openPorts.joinToString(", ")}",
        color = if (host.isRtspCamera) ThreatCritical else AlertAmber,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp
      )

      if (host.vendorGuess.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "CLASS: ${host.vendorGuess} ${if (host.banner.isNotBlank()) "- ${host.banner}" else ""}",
          color = TextMuted,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      }
    }
  }
}
