package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.DetectedSignalEntity
import com.example.core.database.DiscoveredDeviceEntity
import com.example.core.database.ReconLogEntity
import com.example.core.database.ReconRepository
import com.example.core.model.ThreatLevel
import com.example.engine.AiTaggingState
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PersistentReconLogsScreen(
  repository: ReconRepository,
  onOpenDrawer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableStateOf("DEVICES") } // DEVICES, SIGNALS, LOGS
  var selectedAiFilter by remember { mutableStateOf("ALL") }
  val scope = rememberCoroutineScope()

  val signals by repository.recentSignals.collectAsState(initial = emptyList())
  val devices by repository.allDevices.collectAsState(initial = emptyList())
  val logs by repository.recentLogs.collectAsState(initial = emptyList())
  val signalCount by repository.signalCount.collectAsState(initial = 0)
  val deviceCount by repository.totalDeviceCount.collectAsState(initial = 0)
  val aiState by (repository.aiState?.collectAsState() ?: remember { mutableStateOf(AiTaggingState()) })

  var inspectingDevice by remember { mutableStateOf<DiscoveredDeviceEntity?>(null) }

  val filteredDevices = remember(devices, selectedAiFilter) {
    if (selectedAiFilter == "ALL") devices
    else devices.filter { it.aiTag.equals(selectedAiFilter, ignoreCase = true) }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
  ) {
    item {
      TacticalHeader(
        threatLevel = ThreatLevel.NOMINAL,
        title = "M12: ROOM RECON VAULT",
        subtitle = "PERSISTENT DATABASE // AUTOMATED AI TAGGING",
        rfDensity = "$signalCount SIGNALS",
        subnet = "$deviceCount DEVICES",
        onMenuClick = onOpenDrawer
      )
    }

    // Storage Statistics HUD
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        DbStatCard("SAVED SIGNALS", "$signalCount", RadarCyan, Modifier.weight(1f))
        DbStatCard("TRACKED DEVICES", "$deviceCount", Color(0xFFFF9800), Modifier.weight(1f))
        DbStatCard("AUDIT ENTRIES", "${logs.size}", Color(0xFF00E676), Modifier.weight(1f))
      }
    }

    // Automated AI Tagging Engine HUD & Control Box
    item {
      AiInferenceEngineControlCard(
        aiState = aiState,
        totalDevices = devices.size,
        onTriggerBatch = {
          repository.triggerBatchAiInference()
        }
      )
    }

    // Vault Tab Switcher & Actions
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VaultTabButton("AI DEVICES (${devices.size})", selectedTab == "DEVICES", Modifier.weight(1f)) { selectedTab = "DEVICES" }
        VaultTabButton("SIGNALS (${signals.size})", selectedTab == "SIGNALS", Modifier.weight(1f)) { selectedTab = "SIGNALS" }
        VaultTabButton("LOGS (${logs.size})", selectedTab == "LOGS", Modifier.weight(1f)) { selectedTab = "LOGS" }

        IconButton(
          onClick = {
            scope.launch {
              when (selectedTab) {
                "SIGNALS" -> repository.clearSignals()
                "DEVICES" -> repository.clearDevices()
                "LOGS" -> repository.clearLogs()
              }
            }
          },
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurface)
            .border(1.dp, SignalRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        ) {
          Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = "Clear Vault Table",
            tint = SignalRed,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // AI Filter Chips Row when in DEVICES tab
    if (selectedTab == "DEVICES") {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val tagOptions = listOf("ALL", "Camera", "Smartphone", "IoT", "Laptop/PC", "Tracker/Beacon", "Audio Bug", "Router/AP", "Unknown")
          tagOptions.forEach { tag ->
            val count = if (tag == "ALL") devices.size else devices.count { it.aiTag.equals(tag, ignoreCase = true) }
            AiFilterChip(
              label = if (tag == "ALL") "ALL ($count)" else "$tag ($count)",
              isSelected = selectedAiFilter.equals(tag, ignoreCase = true),
              onClick = { selectedAiFilter = tag }
            )
          }
        }
      }
    }

    // Content Display
    when (selectedTab) {
      "DEVICES" -> {
        if (filteredDevices.isEmpty()) {
          item { EmptyVaultPlaceholder("NO DEVICES MATCHING '$selectedAiFilter' FILTER IN ROOM DB") }
        } else {
          items(filteredDevices, key = { it.macOrId }) { dev ->
            DiscoveredDeviceCard(
              dev = dev,
              onInspect = { inspectingDevice = dev },
              onReInfer = {
                scope.launch {
                  repository.inferSingleDevice(dev.macOrId)
                }
              }
            )
          }
        }
      }
      "SIGNALS" -> {
        if (signals.isEmpty()) {
          item { EmptyVaultPlaceholder("NO CAPTURED SIGNALS IN ROOM DATABASE") }
        } else {
          items(signals, key = { it.id }) { sig ->
            SignalLogCard(sig = sig)
          }
        }
      }
      "LOGS" -> {
        if (logs.isEmpty()) {
          item { EmptyVaultPlaceholder("NO AUDIT LOGS RECORDED") }
        } else {
          items(logs, key = { it.id }) { log ->
            ReconLogCard(log = log)
          }
        }
      }
    }
  }

  // Device AI Reasoning Inspection Modal
  inspectingDevice?.let { dev ->
    AlertDialog(
      onDismissRequest = { inspectingDevice = null },
      containerColor = DarkSurface,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = getAiTagIcon(dev.aiTag),
            contentDescription = null,
            tint = getAiTagColor(dev.aiTag),
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = dev.name.ifBlank { dev.macOrId },
              color = Color.White,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
            Text(
              text = "AI CLASSIFICATION: ${dev.aiTag.uppercase()}",
              color = getAiTagColor(dev.aiTag),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Black,
              fontSize = 10.sp
            )
          }
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070B0E))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("AI CONFIDENCE:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text(
              text = "${(dev.aiConfidence * 100).toInt()}% (${if (dev.isAiVerified) "VERIFIED" else "PROVISIONAL"})",
              color = if (dev.aiConfidence >= 0.8f) Color(0xFF00E676) else Color(0xFFFF9800),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            )
          }

          HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)

          Text("METADATA INFERENCE REASONING:", color = RadarCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
          Text(
            text = dev.aiInferenceReasoning.ifBlank { "Automated rule evaluation pending deep packet capture." },
            color = Color(0xFFE4E4E7),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
          )

          HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)

          Text("PHYSICAL ATTRIBUTES:", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
          Text("• MAC / ID: ${dev.macOrId}", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          Text("• OUI VENDOR: ${dev.vendor}", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          Text("• MEDIUM: ${dev.medium} | IP: ${dev.ipAddress.ifBlank { "N/A" }}", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          Text("• OPEN PORTS: ${dev.openPortsJson}", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
          Text("• PROXIMITY: ≈ ${dev.distanceMeters}m (${dev.roomProximityZone})", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch {
              repository.inferSingleDevice(dev.macOrId)
              inspectingDevice = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = RadarCyan, contentColor = Color.Black),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text("RE-RUN INFERENCE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = { inspectingDevice = null }) {
          Text("CLOSE", color = TextMuted, fontFamily = FontFamily.Monospace)
        }
      }
    )
  }
}

@Composable
private fun AiInferenceEngineControlCard(
  aiState: AiTaggingState,
  totalDevices: Int,
  onTriggerBatch: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ai_pulse_alpha"
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(DarkSurface)
      .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
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
            .background(if (aiState.isInferring) Color(0xFFFF9800).copy(alpha = pulseAlpha) else Color(0xFF00E676))
        )
        Text(
          text = "AI INFERENCE ENGINE // ROOM DB AUTO-TAGGER",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          color = Color.White
        )
      }

      Text(
        text = if (aiState.isInferring) "INFERRING..." else "READY",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = if (aiState.isInferring) Color(0xFFFF9800) else RadarCyan
      )
    }

    Text(
      text = "Automatically labels discovered emitters via on-device neural heuristics + Gemini metadata analysis.",
      fontFamily = FontFamily.Monospace,
      fontSize = 9.5.sp,
      color = TextSecondary
    )

    // Tag breakdown summary metrics
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      AiPillMetric("CAMS", "${aiState.cameraCount}", SignalRed, Modifier.weight(1f))
      AiPillMetric("PHONES", "${aiState.smartphoneCount}", RadarCyan, Modifier.weight(1f))
      AiPillMetric("IOT", "${aiState.iotCount}", Color(0xFFFF9800), Modifier.weight(1f))
      AiPillMetric("PCS", "${aiState.laptopCount}", Color(0xFF7C4DFF), Modifier.weight(1f))
      AiPillMetric("TAGS", "${aiState.trackerCount}", Color(0xFFFF4081), Modifier.weight(1f))
    }

    Button(
      onClick = onTriggerBatch,
      enabled = !aiState.isInferring && totalDevices > 0,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF00E5FF),
        contentColor = Color.Black,
        disabledContainerColor = Color(0x3300E5FF),
        disabledContentColor = Color(0x6600E5FF)
      ),
      shape = RoundedCornerShape(4.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (aiState.isInferring) {
          CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Color.Black
          )
        } else {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Text(
          text = if (aiState.isInferring) "INFERRING METADATA ON ${aiState.lastInferredDevice}..." else "RUN AI INFERENCE ON ALL ($totalDevices) DEVICES",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Black,
          fontSize = 10.sp
        )
      }
    }
  }
}

@Composable
private fun AiPillMetric(label: String, count: String, color: Color, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(4.dp))
      .background(Color(0xFF0A0F14))
      .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = count, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp, color = color)
    Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 7.sp, color = TextMuted)
  }
}

@Composable
private fun AiFilterChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) Color(0x3300E5FF) else DarkSurface)
      .border(1.dp, if (isSelected) RadarCyan else DarkCardBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 5.dp)
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
private fun DbStatCard(label: String, count: String, color: Color, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = count,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Black,
      fontSize = 16.sp,
      color = color
    )
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 8.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
      maxLines = 1
    )
  }
}

@Composable
private fun VaultTabButton(
  label: String,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
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
      fontSize = 10.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) RadarCyan else Color(0xFFA1A1AA)
    )
  }
}

@Composable
private fun DiscoveredDeviceCard(
  dev: DiscoveredDeviceEntity,
  onInspect: () -> Unit,
  onReInfer: () -> Unit
) {
  val tagColor = getAiTagColor(dev.aiTag)
  val tagIcon = getAiTagIcon(dev.aiTag)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(
        1.dp,
        if (dev.aiTag == "Camera" || dev.isSuspect) SignalRed else DarkCardBorder,
        RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onInspect)
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(
          imageVector = tagIcon,
          contentDescription = dev.aiTag,
          tint = tagColor,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = dev.name.ifBlank { dev.macOrId },
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1
        )
      }

      // AI Tag Pill with Confidence
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(tagColor.copy(alpha = 0.15f))
          .border(1.dp, tagColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = dev.aiTag.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = tagColor
          )
          Text(
            text = "${(dev.aiConfidence * 100).toInt()}%",
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            color = Color(0xFFA1A1AA)
          )
        }
      }
    }

    // Second line: Medium + Vendor + Proximity Zone
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${dev.medium} // ${dev.macOrId}",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = TextMuted
      )
      Text(
        text = "≈ ${dev.distanceMeters}m (${dev.roomProximityZone})",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = if (dev.roomProximityZone == "IMMEDIATE") SignalRed else RadarCyan
      )
    }

    // Third line: AI reasoning snippet + Quick Inspect / Re-infer
    if (dev.aiInferenceReasoning.isNotBlank()) {
      Text(
        text = "AI: ${dev.aiInferenceReasoning}",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = Color(0xFFD4D4D8),
        maxLines = 2
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "VENDOR: ${dev.vendor}",
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp,
        color = Color(0xFFA1A1AA)
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "[VIEW REASONING]",
          fontFamily = FontFamily.Monospace,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.Bold,
          color = RadarCyan,
          modifier = Modifier.clickable(onClick = onInspect)
        )
      }
    }
  }
}

@Composable
private fun SignalLogCard(sig: DetectedSignalEntity) {
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
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (sig.threatScore > 50) SignalRed else RadarCyan)
        )
        Text(
          text = sig.displayName,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
      Text(
        text = sig.formattedTime,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = TextMuted
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${sig.signalType} // ${sig.identifier}",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = TextMuted
      )
      Text(
        text = "${sig.rssi} dBm",
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = RadarCyan
      )
    }
  }
}

@Composable
private fun ReconLogCard(log: ReconLogEntity) {
  val levelColor = when (log.level) {
    "CRITICAL", "ALERT" -> SignalRed
    "WARN" -> Color(0xFFFF9800)
    "AUDIT" -> RadarCyan
    else -> Color(0xFF00E676)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(DarkSurface)
      .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
      .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
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
        Text(
          text = "[${log.level}]",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = levelColor
        )
        Text(
          text = "${log.subsystem} // ${log.tag}",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
      Text(
        text = log.formattedTime,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.sp,
        color = TextMuted
      )
    }
    Text(
      text = log.message,
      fontFamily = FontFamily.Monospace,
      fontSize = 9.5.sp,
      color = Color(0xFFD4D4D8)
    )
  }
}

@Composable
private fun EmptyVaultPlaceholder(message: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(DarkSurface)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = message,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
      color = TextMuted
    )
  }
}

fun getAiTagColor(tag: String): Color {
  return when (tag.lowercase()) {
    "camera" -> Color(0xFFFF2A3C)
    "smartphone" -> Color(0xFF00E5FF)
    "iot" -> Color(0xFFFF9800)
    "laptop/pc", "laptop", "pc" -> Color(0xFF7C4DFF)
    "tracker/beacon", "tracker", "beacon" -> Color(0xFFFF4081)
    "audio bug", "bug" -> Color(0xFFFF5252)
    "router/ap", "router", "ap" -> Color(0xFF00E676)
    else -> Color(0xFFA1A1AA)
  }
}

fun getAiTagIcon(tag: String): ImageVector {
  return when (tag.lowercase()) {
    "camera" -> Icons.Default.Videocam
    "smartphone" -> Icons.Default.PhoneAndroid
    "iot" -> Icons.Default.Sensors
    "laptop/pc", "laptop", "pc" -> Icons.Default.Computer
    "tracker/beacon", "tracker", "beacon" -> Icons.Default.GpsFixed
    "audio bug", "bug" -> Icons.Default.Mic
    "router/ap", "router", "ap" -> Icons.Default.Wifi
    else -> Icons.Default.HelpOutline
  }
}
