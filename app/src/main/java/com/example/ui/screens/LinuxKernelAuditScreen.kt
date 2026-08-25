package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ThreatLevel
import com.example.engine.AuditCommandResult
import com.example.engine.KernelAuditReport
import com.example.engine.LinuxKernelAuditEngine
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LinuxKernelAuditScreen(
  engine: LinuxKernelAuditEngine,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val report by engine.auditReport.collectAsState()
  val history by engine.commandHistory.collectAsState()
  var inputCommand by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(history.size) {
    if (history.isNotEmpty()) {
      listState.animateScrollToItem(history.size)
    }
  }

  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
  ) {
    item {
      TacticalHeader(
        threatLevel = if (report.hardeningScore < 80) ThreatLevel.WARN else ThreatLevel.NOMINAL,
        title = "M11: KERNEL SECURITY AUDIT",
        subtitle = "LINUX SUBSYSTEM INSPECTOR & SHELL",
        rfDensity = "${report.activeSocketsCount} SOCKETS",
        subnet = "SELINUX: ${report.selinuxMode.take(9)}",
        onMenuClick = onOpenDrawer
      )
    }

    // Kernel Security Metrics HUD
    item {
      KernelStatusHud(report = report, onRefresh = { engine.performFullAudit() })
    }

    // Quick Action Audit Chips
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "TACTICAL AUDIT MACROS:",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFFA1A1AA)
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          QuickAuditChip("audit selinux") { engine.executeCommand("audit selinux") }
          QuickAuditChip("netstat") { engine.executeCommand("netstat") }
          QuickAuditChip("arp -a") { engine.executeCommand("arp") }
          QuickAuditChip("audit entropy") { engine.executeCommand("audit entropy") }
          QuickAuditChip("free -m") { engine.executeCommand("free") }
          QuickAuditChip("audit sockets") { engine.executeCommand("audit sockets") }
          QuickAuditChip("uname -a") { engine.executeCommand("uname") }
          QuickAuditChip("ifconfig") { engine.executeCommand("ifconfig") }
          QuickAuditChip("room-audit") { engine.executeCommand("room-audit") }
        }
      }
    }

    // Interactive Terminal Output Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "INTERACTIVE KERNEL AUDIT SHELL",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = RadarCyan
        )
        Text(
          text = "root@aegis-linux:~#",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = SignalRed
        )
      }
    }

    // Command History Outputs
    items(history) { res ->
      AuditConsoleEntry(res = res)
    }

    // Interactive Shell Input Box
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF090D10))
          .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "root@aegis:~# ",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SignalRed
          )

          TextField(
            value = inputCommand,
            onValueChange = { inputCommand = it },
            placeholder = {
              Text(
                text = "Enter audit command (e.g. 'audit selinux', 'help')",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF52525B)
              )
            },
            colors = TextFieldDefaults.colors(
              focusedContainerColor = Color.Transparent,
              unfocusedContainerColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
              onSend = {
                if (inputCommand.isNotBlank()) {
                  engine.executeCommand(inputCommand)
                  inputCommand = ""
                  keyboardController?.hide()
                }
              }
            ),
            modifier = Modifier.weight(1f)
          )

          IconButton(
            onClick = {
              if (inputCommand.isNotBlank()) {
                engine.executeCommand(inputCommand)
                inputCommand = ""
                keyboardController?.hide()
              }
            }
          ) {
            Icon(
              imageVector = Icons.Default.Send,
              contentDescription = "Execute Command",
              tint = RadarCyan
            )
          }
        }
      }
    }
  }
}

@Composable
private fun KernelStatusHud(
  report: KernelAuditReport,
  onRefresh: () -> Unit
) {
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
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(RadarCyan)
        )
        Text(
          text = "KERNEL ENVIRONMENT TELEMETRY",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      IconButton(
        onClick = onRefresh,
        modifier = Modifier.size(24.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Refresh Kernel Audit",
          tint = RadarCyan,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    HorizontalDivider(color = Color(0x1AFFFFFF))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      KernelMetricTile("HARDENING SCORE", "${report.hardeningScore}%", RadarCyan, Modifier.weight(1f))
      KernelMetricTile("ENTROPY POOL", "${report.entropyBits} bits", Color(0xFF00E676), Modifier.weight(1f))
      KernelMetricTile("SELINUX STATE", report.selinuxMode.take(12), Color(0xFFFF9800), Modifier.weight(1f))
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      KernelMetricTile("UPTIME", report.uptimeFormatted, Color.White, Modifier.weight(1f))
      KernelMetricTile("MEMORY AVAIL", "${report.memoryAvailableMb}MB / ${report.memoryTotalMb}MB", Color(0xFFA1A1AA), Modifier.weight(1f))
      KernelMetricTile("KPTR RESTRICT", report.kptrRestrictValue, Color(0xFF00E5FF), Modifier.weight(1f))
    }

    Text(
      text = "BANNER: ${report.kernelRelease}",
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      color = TextMuted,
      maxLines = 1
    )
  }
}

@Composable
private fun KernelMetricTile(
  label: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(4.dp))
      .background(Color(0xFF0D1217))
      .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(4.dp))
      .padding(6.dp)
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 7.5.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
      maxLines = 1
    )
    Text(
      text = value,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
      fontWeight = FontWeight.Black,
      color = color,
      maxLines = 1
    )
  }
}

@Composable
private fun QuickAuditChip(command: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(Color(0x1A00E5FF))
      .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    Text(
      text = "# $command",
      fontFamily = FontFamily.Monospace,
      fontSize = 9.5.sp,
      fontWeight = FontWeight.Bold,
      color = RadarCyan
    )
  }
}

@Composable
private fun AuditConsoleEntry(res: AuditCommandResult) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF080C0F))
      .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(6.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "root@aegis:~# ${res.command}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = SignalRed
      )
      Text(
        text = res.timestamp,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.sp,
        color = TextMuted
      )
    }

    HorizontalDivider(color = Color(0x0DFFFFFF))

    Text(
      text = res.output,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      lineHeight = 14.sp,
      color = Color(0xFFD4D4D8)
    )
  }
}
