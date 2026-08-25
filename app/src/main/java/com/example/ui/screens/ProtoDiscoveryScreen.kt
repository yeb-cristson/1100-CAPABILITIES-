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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.core.model.ProtoService
import com.example.engine.ProtoEngine
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
fun ProtoDiscoveryScreen(
  engine: ProtoEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val services by engine.services.collectAsState()
  val isDiscovering by engine.isDiscovering.collectAsState()
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
              text = "M4 // PROTOCOL DISCOVERY",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "mDNS (DNS-SD) + SSDP MULTICAST (1900)",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              onClick = {
                if (isDiscovering) engine.stopDiscovery() else engine.startDiscovery()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isDiscovering) ThreatCritical else SignalRed,
                contentColor = TextPrimary
              ),
              shape = RoundedCornerShape(6.dp)
            ) {
              Icon(
                imageVector = if (isDiscovering) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isDiscovering) "STOP" else "SWEEP",
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
      }
    }

    if (services.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No mDNS / SSDP broadcast services detected yet.",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
          )
        }
      }
    } else {
      items(services, key = { it.id }) { service ->
        ProtoServiceCard(service = service)
      }
    }
  }
}

@Composable
private fun ProtoServiceCard(service: ProtoService) {
  val isSurveillance = service.vendorGuess.contains("Camera", true) ||
    service.serviceType.contains("onvif", true) ||
    service.serviceType.contains("rtsp", true)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(DarkSurface, RoundedCornerShape(6.dp))
      .border(
        width = 1.dp,
        color = if (isSurveillance) ThreatCritical else DarkCardBorder,
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
          if (isSurveillance) {
            Icon(Icons.Default.Videocam, contentDescription = null, tint = ThreatCritical, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(
            text = service.info,
            color = if (isSurveillance) ThreatCritical else TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }

        Text(
          text = service.protocol,
          color = RadarCyan,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "${service.host}:${service.port} | ${service.serviceType}",
        color = AlertAmber,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp
      )

      if (service.vendorGuess.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "VENDOR / CLASSIFICATION: ${service.vendorGuess}",
          color = TextMuted,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      }
    }
  }
}
