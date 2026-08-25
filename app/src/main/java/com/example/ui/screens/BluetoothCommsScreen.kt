package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.engine.BluetoothCommsEngine
import com.example.engine.BluetoothPeer
import com.example.engine.TacticalMessage
import com.example.engine.TacticalMessageType
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*

@Composable
fun BluetoothCommsScreen(
  engine: BluetoothCommsEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val state by engine.state.collectAsState()
  var inputMessage by remember { mutableStateOf("") }

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
        threatLevel = if (state.isConnected) ThreatLevel.NOMINAL else ThreatLevel.INFO,
        title = "M15: BLUETOOTH TACTICAL COMMS",
        subtitle = "RFCOMM MESH // SECURE REAL-TIME LINK",
        rfDensity = if (state.isConnected) "LINK ONLINE" else "DISCONNECTED",
        subnet = "${state.peers.size} PEERS",
        onMenuClick = onOpenDrawer
      )
    }

    // Bluetooth Hardware & RFCOMM Server Control HUD
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, if (state.isConnected) Color(0xFF00E676).copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(8.dp))
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
                .background(
                  when {
                    state.isConnected -> Color(0xFF00E676)
                    state.isServerListening -> Color(0xFFFF9800)
                    else -> SignalRed
                  }
                )
            )
            Text(
              text = if (state.isConnected) "CONNECTED: ${state.connectedPeerName}" else if (state.isServerListening) "SERVER: LISTENING FOR PEERS" else "SERVER: OFFLINE",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color.White
            )
          }

          Text(
            text = state.channelId,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = RadarCyan
          )
        }

        Text(
          text = state.statusMessage,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = TextSecondary
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              if (state.isServerListening) engine.stopServer() else engine.startServer()
            },
            enabled = !state.isConnected,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (state.isServerListening) ThreatCritical else Color(0xFF7C4DFF),
              contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.weight(1f)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(
                imageVector = if (state.isServerListening) Icons.Default.Stop else Icons.Default.Sensors,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (state.isServerListening) "STOP SERVER" else "START BT SERVER",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
              )
            }
          }

          Button(
            onClick = { engine.startPeerDiscovery() },
            colors = ButtonDefaults.buttonColors(
              containerColor = RadarCyan,
              contentColor = Color.Black
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.weight(1f)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Text(
                text = if (state.isScanning) "SCANNING..." else "DISCOVER PEERS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
              )
            }
          }

          if (state.isConnected) {
            IconButton(
              onClick = { engine.disconnect() },
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThreatCritical.copy(alpha = 0.2f))
                .border(1.dp, ThreatCritical, RoundedCornerShape(4.dp))
            ) {
              Icon(Icons.Default.LinkOff, contentDescription = "Disconnect", tint = ThreatCritical, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    }

    // Nearby / Paired Tactical Peers Section
    item {
      Text(
        text = "DISCOVERED TACTICAL NODES (${state.peers.size})",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = RadarCyan
      )
    }

    if (state.peers.isEmpty()) {
      item {
        EmptyBtPlaceholder("NO PEERS IN RANGE // TAP 'DISCOVER PEERS' OR PAIR IN ANDROID SETTINGS")
      }
    } else {
      items(state.peers, key = { it.address }) { peer ->
        BluetoothPeerCard(
          peer = peer,
          isConnected = state.connectedPeerAddress == peer.address,
          onConnect = { engine.connectToPeer(peer.address) }
        )
      }
    }

    // Tactical Real-Time Message Stream Section
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "REAL-TIME TACTICAL CHAT // MESH STREAM",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.White
          )

          // Quick Threat Broadcast Action
          Text(
            text = "[BROADCAST INTEL]",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = SignalRed,
            modifier = Modifier.clickable {
              engine.broadcastIntelThreat("Covert RTSP Camera", "192.168.1.105", 95, "Active 554 video feed detected")
            }
          )
        }

        // Quick Presets
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          QuickMsgPill("ALL CLEAR", Modifier.weight(1f)) { engine.sendMessage("ALL CLEAR // PERIMETER SECURE") }
          QuickMsgPill("THREAT DETECTED", Modifier.weight(1.2f)) { engine.sendMessage("THREAT DETECTED // RF SPIKE LOCATED", TacticalMessageType.INTEL_ALERT) }
          QuickMsgPill("SYNC VAULT", Modifier.weight(1f)) { engine.sendMessage("REQUESTING RECON VAULT SYNC", TacticalMessageType.DEVICE_SYNC) }
        }

        // Message input row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = inputMessage,
            onValueChange = { inputMessage = it },
            placeholder = {
              Text(
                text = if (state.isConnected) "Type tactical transmission..." else "Type message (local broadcast)...",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
              )
            },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = RadarCyan,
              unfocusedBorderColor = DarkCardBorder,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              cursorColor = RadarCyan
            ),
            shape = RoundedCornerShape(4.dp)
          )

          Button(
            onClick = {
              if (inputMessage.isNotBlank()) {
                engine.sendMessage(inputMessage)
                inputMessage = ""
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan, contentColor = Color.Black),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(52.dp)
          ) {
            Icon(Icons.Default.Send, contentDescription = "Send")
          }
        }
      }
    }

    // Message Feed
    if (state.messages.isEmpty()) {
      item {
        EmptyBtPlaceholder("NO TRANSMISSIONS RECORDED ON THIS CHANNEL")
      }
    } else {
      items(state.messages.reversed(), key = { it.id }) { msg ->
        TacticalMessageCard(msg = msg)
      }
    }
  }
}

@Composable
private fun BluetoothPeerCard(
  peer: BluetoothPeer,
  isConnected: Boolean,
  onConnect: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(1.dp, if (isConnected) Color(0xFF00E676) else DarkCardBorder, RoundedCornerShape(6.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
          imageVector = Icons.Default.Bluetooth,
          contentDescription = null,
          tint = if (isConnected) Color(0xFF00E676) else RadarCyan,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = peer.name,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(3.dp))
          .background(if (peer.bondState == "PAIRED") Color(0x3300E676) else Color(0x33A1A1AA))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = peer.bondState,
          fontFamily = FontFamily.Monospace,
          fontSize = 8.sp,
          color = if (peer.bondState == "PAIRED") Color(0xFF00E676) else Color(0xFFA1A1AA)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = peer.address,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = TextMuted
      )

      if (!isConnected) {
        Text(
          text = "[CONNECT RFCOMM]",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = RadarCyan,
          modifier = Modifier.clickable(onClick = onConnect)
        )
      } else {
        Text(
          text = "ACTIVE LINK",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF00E676)
        )
      }
    }
  }
}

@Composable
private fun TacticalMessageCard(msg: TacticalMessage) {
  val typeColor = when (msg.type) {
    TacticalMessageType.INTEL_ALERT -> SignalRed
    TacticalMessageType.DEVICE_SYNC -> Color(0xFFFF9800)
    TacticalMessageType.SYSTEM_STATUS -> Color(0xFF00E676)
    TacticalMessageType.HEARTBEAT -> Color(0xFF7C4DFF)
    TacticalMessageType.CHAT -> if (msg.isLocalUser) RadarCyan else Color.White
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(
        1.dp,
        if (msg.type == TacticalMessageType.INTEL_ALERT) SignalRed.copy(alpha = 0.5f) else DarkCardBorder,
        RoundedCornerShape(6.dp)
      )
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "[${msg.type.name}]",
          fontFamily = FontFamily.Monospace,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.Bold,
          color = typeColor
        )
        Text(
          text = if (msg.isLocalUser) "YOU (${msg.senderName})" else msg.senderName,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (msg.isLocalUser) RadarCyan else Color(0xFFE4E4E7)
        )
      }

      Text(
        text = msg.formattedTime,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        color = TextMuted
      )
    }

    Text(
      text = msg.content,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      color = Color.White
    )
  }
}

@Composable
private fun QuickMsgPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(4.dp))
      .background(Color(0xFF0C1016))
      .border(0.5.dp, DarkCardBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 8.sp,
      fontWeight = FontWeight.Bold,
      color = RadarCyan
    )
  }
}

@Composable
private fun EmptyBtPlaceholder(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .padding(16.dp),
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
