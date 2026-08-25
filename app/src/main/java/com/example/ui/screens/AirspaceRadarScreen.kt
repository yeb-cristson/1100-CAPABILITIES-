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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.RfDevice
import com.example.core.model.RfType
import com.example.engine.AirspaceEngine
import com.example.ui.components.RssiSignalBar
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import com.example.ui.theme.SignalRedContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThreatCritical

@Composable
fun AirspaceRadarScreen(
  engine: AirspaceEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val devices by engine.devices.collectAsState()
  val isScanning by engine.isScanning.collectAsState()
  val statusMessage by engine.statusMessage.collectAsState()

  var selectedFilter by remember { mutableStateOf<RfType?>(null) }

  val filteredDevices = remember(devices, selectedFilter) {
    if (selectedFilter == null) devices else devices.filter { it.type == selectedFilter }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Header Controls
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
              text = "M1 // AIRSPACE RADAR",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "${devices.size} REAL RF EMITTERS CAPTURED",
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
                text = if (isScanning) "STOP" else "SCAN",
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

        Text(
          text = statusMessage,
          color = TextSecondary,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedFilter == null,
            onClick = { selectedFilter = null },
            label = { Text("ALL (${devices.size})", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = SignalRedContainer,
              selectedLabelColor = SignalRed
            )
          )
          FilterChip(
            selected = selectedFilter == RfType.BLE,
            onClick = { selectedFilter = RfType.BLE },
            label = { Text("BLE (${devices.count { it.type == RfType.BLE }})", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = SignalRedContainer,
              selectedLabelColor = SignalRed
            )
          )
          FilterChip(
            selected = selectedFilter == RfType.WIFI,
            onClick = { selectedFilter = RfType.WIFI },
            label = { Text("WIFI (${devices.count { it.type == RfType.WIFI }})", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = SignalRedContainer,
              selectedLabelColor = SignalRed
            )
          )
        }
      }
    }

    if (filteredDevices.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (isScanning) "Listening on 2.4 GHz & 5 GHz channels..." else "Airspace monitor standby. Tap SCAN to begin.",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
          )
        }
      }
    } else {
      items(filteredDevices, key = { it.id }) { device ->
        RfDeviceCard(device = device)
      }
    }
  }
}

@Composable
private fun RfDeviceCard(device: RfDevice) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(DarkSurface, RoundedCornerShape(6.dp))
      .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
      .padding(12.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = if (device.type == RfType.BLE) Icons.Default.Bluetooth else Icons.Default.Wifi,
            contentDescription = null,
            tint = if (device.type == RfType.BLE) RadarCyan else AlertAmber,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = device.name,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }

        RssiSignalBar(rssi = device.rssi)
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = device.id,
          color = TextMuted,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )

        val distStr = if (device.distanceMeters > 0) "%.1f m est.".format(device.distanceMeters) else "N/A"
        Text(
          text = distStr,
          color = RadarCyan,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      }

      if (device.rawData.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "RAW: ${device.rawData.take(36)}",
          color = TextMuted,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp
        )
      }
    }
  }
}
