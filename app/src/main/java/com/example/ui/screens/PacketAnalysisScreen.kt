package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ThreatLevel
import com.example.engine.CapturedPacket
import com.example.engine.HostTrafficFootprint
import com.example.engine.PacketAnalysisEngine
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*

@Composable
fun PacketAnalysisScreen(
  engine: PacketAnalysisEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val isCapturing by engine.isCapturing.collectAsState()
  val packets by engine.packets.collectAsState()
  val footprintsMap by engine.hostFootprints.collectAsState()
  val totalPackets by engine.totalPacketsCaptured.collectAsState()
  val totalBytes by engine.totalBytesCaptured.collectAsState()
  val socketStatus by engine.promiscuousStatus.collectAsState()

  var selectedProtocolFilter by remember { mutableStateOf("ALL") }
  var inspectingPacket by remember { mutableStateOf<CapturedPacket?>(null) }
  var showHostFootprints by remember { mutableStateOf(false) }

  val filteredPackets = remember(packets, selectedProtocolFilter) {
    if (selectedProtocolFilter == "ALL") packets
    else packets.filter { it.protocol.equals(selectedProtocolFilter, ignoreCase = true) }
  }

  val footprints = remember(footprintsMap) { footprintsMap.values.toList().sortedByDescending { it.lastSeen } }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
  ) {
    item {
      TacticalHeader(
        threatLevel = if (packets.any { it.isThreatAnomaly }) ThreatLevel.WARN else ThreatLevel.NOMINAL,
        title = "M14: PACKET HEADER ANALYZER",
        subtitle = "RAW SOCKET SNIFFER // ACTIVE HOST FOOTPRINTS",
        rfDensity = "$totalPackets PACKETS",
        subnet = "${footprints.size} ACTIVE IPS",
        onMenuClick = onOpenDrawer
      )
    }

    // Engine Control & Metrics HUD
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, if (isCapturing) RadarCyan.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(8.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isCapturing) Color(0xFF00E676) else SignalRed)
            )
            Text(
              text = if (isCapturing) "SOCKET SNIFFER: ACTIVE" else "SOCKET SNIFFER: STANDBY",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color.White
            )
          }

          Text(
            text = socketStatus,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            color = TextMuted
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          PacketMetricCard("TOTAL PACKETS", "$totalPackets", RadarCyan, Modifier.weight(1f))
          PacketMetricCard("DATA VOLUME", formatBytes(totalBytes), Color(0xFFFF9800), Modifier.weight(1f))
          PacketMetricCard("HOST FOOTPRINTS", "${footprints.size}", Color(0xFF00E676), Modifier.weight(1f))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              if (isCapturing) engine.stopCapture() else engine.startCapture()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isCapturing) ThreatCritical else RadarCyan,
              contentColor = if (isCapturing) TextPrimary else Color.Black
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.weight(1f)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(
                imageVector = if (isCapturing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (isCapturing) "STOP SNIFFER" else "START RAW SNIFFER",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }
          }

          IconButton(
            onClick = { engine.clearPackets() },
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF13181E))
              .border(1.dp, DarkCardBorder, RoundedCornerShape(4.dp))
          ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Packets", tint = SignalRed, modifier = Modifier.size(18.dp))
          }
        }
      }
    }

    // Toggle between Live Packet Feed and Host Footprint Matrix
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ViewSwitcherButton("LIVE PACKET FEED (${packets.size})", !showHostFootprints, Modifier.weight(1f)) {
          showHostFootprints = false
        }
        ViewSwitcherButton("HOST FOOTPRINTS (${footprints.size})", showHostFootprints, Modifier.weight(1f)) {
          showHostFootprints = true
        }
      }
    }

    // Protocol Filter Chips
    if (!showHostFootprints) {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val protos = listOf("ALL", "RTSP", "SSDP", "mDNS", "UDP", "TCP", "NetBIOS", "DHCP")
          protos.forEach { p ->
            val count = if (p == "ALL") packets.size else packets.count { it.protocol.equals(p, true) }
            ProtoFilterChip(
              label = "$p ($count)",
              isSelected = selectedProtocolFilter.equals(p, true),
              onClick = { selectedProtocolFilter = p }
            )
          }
        }
      }
    }

    // Main Content
    if (showHostFootprints) {
      if (footprints.isEmpty()) {
        item {
          EmptyPlaceholder("NO ACTIVE HOST FOOTPRINTS DETECTED // START SNIFFER OR SUBNET SWEEP")
        }
      } else {
        items(footprints, key = { it.ip }) { fp ->
          HostFootprintCard(
            footprint = fp,
            onSendProbe = { engine.sendStimulationProbe(fp.ip) }
          )
        }
      }
    } else {
      if (filteredPackets.isEmpty()) {
        item {
          EmptyPlaceholder(if (isCapturing) "LISTENING ON RAW SOCKETS FOR INCOMING HEADERS..." else "SNIFFER IDLE // TAP 'START RAW SNIFFER' TO CAPTURE PACKETS")
        }
      } else {
        items(filteredPackets, key = { it.id }) { pkt ->
          PacketRowCard(
            packet = pkt,
            onClick = { inspectingPacket = pkt }
          )
        }
      }
    }
  }

  // Packet Inspection Modal
  inspectingPacket?.let { pkt ->
    AlertDialog(
      onDismissRequest = { inspectingPacket = null },
      containerColor = DarkSurface,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (pkt.isThreatAnomaly) SignalRed else RadarCyan)
          )
          Text(
            text = "PACKET #${pkt.id} // ${pkt.protocol}",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070A0E))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("TIMESTAMP:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text(pkt.formattedTime, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("SRC -> DST:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("${pkt.sourceIp}:${pkt.sourcePort} -> ${pkt.destIp}:${pkt.destPort}", color = RadarCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("LENGTH / TTL:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("${pkt.packetLength} bytes | TTL=${pkt.ttl}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("OS FINGERPRINT:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text(pkt.osFingerprint, color = Color(0xFF00E676), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
          }

          if (pkt.isThreatAnomaly) {
            Text("THREAT FLAG: ${pkt.threatDetail}", color = SignalRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
          }

          HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)

          Text("PAYLOAD SUMMARY:", color = RadarCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
          Text(
            text = pkt.payloadSummary.ifBlank { "Binary payload / non-ASCII datagram" },
            color = Color(0xFFE4E4E7),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp
          )

          HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)

          Text("HEX HEADER DUMP (48B):", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          Text(
            text = pkt.hexDump,
            color = Color(0xFFA1A1AA),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            lineHeight = 12.sp
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { inspectingPacket = null },
          colors = ButtonDefaults.buttonColors(containerColor = RadarCyan, contentColor = Color.Black),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text("CLOSE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
      }
    )
  }
}

@Composable
private fun PacketRowCard(
  packet: CapturedPacket,
  onClick: () -> Unit
) {
  val protoColor = when (packet.protocol.uppercase()) {
    "RTSP" -> SignalRed
    "SSDP" -> Color(0xFFFF9800)
    "MDNS" -> Color(0xFF00E676)
    "UDP" -> RadarCyan
    "TCP" -> Color(0xFF7C4DFF)
    else -> Color(0xFFA1A1AA)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(
        1.dp,
        if (packet.isThreatAnomaly) SignalRed else DarkCardBorder,
        RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onClick)
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(protoColor.copy(alpha = 0.2f))
            .border(0.5.dp, protoColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
          Text(
            text = packet.protocol,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = protoColor
          )
        }

        Text(
          text = "${packet.sourceIp}:${packet.sourcePort} -> ${packet.destIp}:${packet.destPort}",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Text(
        text = packet.formattedTime,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        color = TextMuted
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "LEN: ${packet.packetLength}B | TTL: ${packet.ttl} (${packet.osFingerprint})",
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        color = TextSecondary
      )
      Text(
        text = "[INSPECT]",
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = RadarCyan
      )
    }

    if (packet.payloadSummary.isNotBlank()) {
      Text(
        text = packet.payloadSummary,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        color = Color(0xFFA1A1AA),
        maxLines = 1
      )
    }
  }
}

@Composable
private fun HostFootprintCard(
  footprint: HostTrafficFootprint,
  onSendProbe: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(
        1.dp,
        if (footprint.isSuspicious) SignalRed else DarkCardBorder,
        RoundedCornerShape(6.dp)
      )
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
          imageVector = if (footprint.isSuspicious) Icons.Default.Videocam else Icons.Default.Computer,
          contentDescription = null,
          tint = if (footprint.isSuspicious) SignalRed else RadarCyan,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = footprint.ip,
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(3.dp))
          .background(Color(0x3300E5FF))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = "${footprint.totalPackets} PKTS",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = RadarCyan
        )
      }
    }

    Text(
      text = "MAC: ${footprint.mac} // OS: ${footprint.osGuess}",
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      color = Color(0xFFD4D4D8)
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "PROTOS: ${footprint.protocolsSeen.joinToString(", ")} | VOL: ${formatBytes(footprint.totalBytes)}",
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        color = TextMuted
      )

      Text(
        text = "[SEND STIM PROBE]",
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFFF9800),
        modifier = Modifier.clickable(onClick = onSendProbe)
      )
    }
  }
}

@Composable
private fun PacketMetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(4.dp))
      .background(Color(0xFF090E14))
      .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
      .padding(6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 14.sp, color = color)
    Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 7.sp, color = TextMuted)
  }
}

@Composable
private fun ViewSwitcherButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Box(
    modifier = modifier
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
      fontSize = 9.5.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) RadarCyan else Color(0xFFA1A1AA)
    )
  }
}

@Composable
private fun ProtoFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) Color(0x3300E5FF) else DarkSurface)
      .border(1.dp, if (isSelected) RadarCyan else DarkCardBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      color = if (isSelected) RadarCyan else Color(0xFFA1A1AA)
    )
  }
}

@Composable
private fun EmptyPlaceholder(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .padding(20.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      color = TextMuted
    )
  }
}

private fun formatBytes(bytes: Long): String {
  return when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
  }
}
