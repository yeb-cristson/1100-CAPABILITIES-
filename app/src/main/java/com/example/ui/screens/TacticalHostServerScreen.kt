package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ThreatLevel
import com.example.engine.ConnectedClient
import com.example.engine.TacticalHostServerEngine
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TacticalHostServerScreen(
  engine: TacticalHostServerEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val state by engine.state.collectAsState()
  var portInput by remember { mutableStateOf("8888") }

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
        threatLevel = if (state.isRunning) ThreatLevel.NOMINAL else ThreatLevel.INFO,
        title = "M16: TACTICAL LAN HOST SERVER",
        subtitle = "EMBEDDED HTTP / MDNS SERVICE BROADCASTER",
        rfDensity = if (state.isRunning) "SERVER: ONLINE" else "SERVER: STANDBY",
        subnet = "${state.activeClients.size} CLIENTS",
        onMenuClick = onOpenDrawer
      )
    }

    // Host Server Control Panel
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, if (state.isRunning) Color(0xFF00E676).copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(8.dp))
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
                .background(if (state.isRunning) Color(0xFF00E676) else SignalRed)
            )
            Text(
              text = if (state.isRunning) "HTTP HOST SERVER: ACTIVE" else "HTTP HOST SERVER: OFFLINE",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color.White
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (state.isNsdBroadcasting) Color(0x3300E5FF) else Color(0x33A1A1AA))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = if (state.isNsdBroadcasting) "mDNS BROADCASTING" else "mDNS IDLE",
              fontFamily = FontFamily.Monospace,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = if (state.isNsdBroadcasting) RadarCyan else Color(0xFFA1A1AA)
            )
          }
        }

        // Live URL Card
        if (state.isRunning) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF080D12))
              .border(1.dp, RadarCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
              .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Tactical Host URL", state.hostUrl))
                Toast.makeText(context, "URL Copied: ${state.hostUrl}", Toast.LENGTH_SHORT).show()
              }
              .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("LOCAL WI-FI ACCESS URL:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
              Text(state.hostUrl, color = RadarCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = RadarCyan, modifier = Modifier.size(18.dp))
          }
        }

        // Metrics Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ServerMetricCard("REQUESTS", "${state.totalRequests}", RadarCyan, Modifier.weight(1f))
          ServerMetricCard("CLIENTS", "${state.activeClients.size}", Color(0xFF00E676), Modifier.weight(1f))
          ServerMetricCard("PORT", "${state.port}", Color(0xFFFF9800), Modifier.weight(1f))
        }

        // Server Start/Stop Controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (!state.isRunning) {
            OutlinedTextField(
              value = portInput,
              onValueChange = { portInput = it },
              label = { Text("Port", fontSize = 10.sp, color = TextMuted) },
              modifier = Modifier.width(90.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RadarCyan,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
              ),
              shape = RoundedCornerShape(4.dp)
            )
          }

          Button(
            onClick = {
              if (state.isRunning) {
                engine.stopServer()
              } else {
                val p = portInput.toIntOrNull() ?: 8888
                engine.startServer(p)
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (state.isRunning) ThreatCritical else Color(0xFF00E676),
              contentColor = if (state.isRunning) TextPrimary else Color.Black
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.weight(1f)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(
                imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (state.isRunning) "TERMINATE SERVER" else "LAUNCH LAN HOST SERVER",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }
          }
        }
      }
    }

    // Active Connected Clients Section
    item {
      Text(
        text = "CONNECTED LAN CLIENTS (${state.activeClients.size})",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = RadarCyan
      )
    }

    if (state.activeClients.isEmpty()) {
      item {
        EmptyServerPlaceholder("NO CLIENTS CONNECTED YET // OPEN ${state.hostUrl} ON ANY DEVICE ON THIS WI-FI")
      }
    } else {
      items(state.activeClients, key = { it.ip }) { client ->
        ClientConnectionCard(client = client)
      }
    }

    // Server Activity Terminal Log
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkSurface)
          .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = RadarCyan, modifier = Modifier.size(16.dp))
            Text(
              text = "SERVER EVENT AUDIT LOG",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color.White
            )
          }
          Text("${state.logs.size} EVENTS", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextMuted)
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF07090C))
            .border(0.5.dp, DarkCardBorder, RoundedCornerShape(4.dp))
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          if (state.logs.isEmpty()) {
            Text("[--:--:--] Host server logs will display here...", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
          } else {
            state.logs.takeLast(10).forEach { logLine ->
              Text(
                text = logLine,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                color = if (logLine.contains("error", true)) SignalRed else Color(0xFFD4D4D8),
                lineHeight = 12.sp
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ClientConnectionCard(client: ConnectedClient) {
  val lastTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(client.lastActive))

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Laptop, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
        Text(client.ip, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(3.dp))
          .background(Color(0x3300E676))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text("${client.requestCount} REQS", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
      }
    }

    Text(
      text = "UA: ${client.userAgent}",
      fontFamily = FontFamily.Monospace,
      fontSize = 8.5.sp,
      color = TextMuted,
      maxLines = 1
    )

    Text(
      text = "LAST ACTIVE: $lastTime",
      fontFamily = FontFamily.Monospace,
      fontSize = 8.sp,
      color = TextSecondary
    )
  }
}

@Composable
private fun ServerMetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
private fun EmptyServerPlaceholder(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(text = text, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, color = TextMuted)
  }
}
