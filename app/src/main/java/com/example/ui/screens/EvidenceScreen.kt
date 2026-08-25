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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.EvidenceEntity
import com.example.core.evidence.EvidenceManager
import com.example.core.model.FusionState
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
import kotlinx.coroutines.launch

@Composable
fun EvidenceScreen(
  evidenceManager: EvidenceManager,
  fusionState: FusionState,
  modifier: Modifier = Modifier,
  onOpenDrawer: () -> Unit = {}
) {
  val evidenceList by evidenceManager.getAllEvidence().collectAsState(initial = emptyList())
  val scope = rememberCoroutineScope()
  var selectedItem by remember { mutableStateOf<EvidenceEntity?>(null) }
  var showClearConfirm by remember { mutableStateOf(false) }

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
              text = "M9 // FORENSIC EVIDENCE VAULT",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "LOCAL ROOM DB // SHA-256 SIGNED LOGS",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              onClick = {
                scope.launch {
                  evidenceManager.captureSnapshot(
                    state = fusionState,
                    title = "Forensic Snapshot #${evidenceList.size + 1}"
                  )
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = SignalRed,
                contentColor = TextPrimary
              ),
              shape = RoundedCornerShape(6.dp)
            ) {
              Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "SIGN & SEAL",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }

            if (evidenceList.isNotEmpty()) {
              IconButton(onClick = { showClearConfirm = true }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all", tint = TextMuted)
              }
            }
          }
        }
      }
    }

    if (evidenceList.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No forensic evidence logs recorded yet. Tap SIGN & SEAL to timestamp current hardware state.",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
          )
        }
      }
    } else {
      items(evidenceList, key = { it.id }) { item ->
        EvidenceItemCard(
          item = item,
          onClick = { selectedItem = item },
          onDelete = { scope.launch { evidenceManager.deleteEvidence(item.id) } }
        )
      }
    }
  }

  // Selected Detail Dialog
  selectedItem?.let { evidence ->
    AlertDialog(
      onDismissRequest = { selectedItem = null },
      containerColor = DarkSurface,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SignalRed, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = evidence.title,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
        }
      },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
            .padding(10.dp)
        ) {
          item {
            Text(
              text = "SHA-256 DIGEST:\n${evidence.sha256Digest}\n",
              color = RadarCyan,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            )
            Text(
              text = evidence.rawTelemetryJson,
              color = TextSecondary,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { selectedItem = null }) {
          Text("CLOSE", color = RadarCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
      }
    )
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      containerColor = DarkSurface,
      title = {
        Text("PURGE ALL EVIDENCE?", color = ThreatCritical, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          "This will irreversibly delete all cryptographically signed evidence records stored in the on-device database.",
          color = TextSecondary,
          fontSize = 12.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch { evidenceManager.clearAll() }
            showClearConfirm = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = ThreatCritical)
        ) {
          Text("PURGE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text("CANCEL", color = TextMuted, fontFamily = FontFamily.Monospace)
        }
      }
    )
  }
}

@Composable
private fun EvidenceItemCard(
  item: EvidenceEntity,
  onClick: () -> Unit,
  onDelete: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(DarkSurface, RoundedCornerShape(6.dp))
      .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
      .clickable { onClick() }
      .padding(12.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SignalRed, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = item.title,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = item.summary,
        color = AlertAmber,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp
      )

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = item.formattedDate,
          color = TextMuted,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp
        )

        Text(
          text = "SHA: ${item.sha256Digest.take(12)}...",
          color = RadarCyan,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp
        )
      }
    }
  }
}
