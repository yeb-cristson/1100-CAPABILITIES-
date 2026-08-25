package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Warning
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
import com.example.core.hub.ReconHub
import com.example.core.model.BleTracker
import com.example.ui.components.SignalQualityMeter
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed

@Composable
fun BleTrackersScreen(
  onOpenDrawer: () -> Unit
) {
  val reconHub = ReconHub.getInstance()
  val trackers by reconHub.bleTrackers.collectAsState()
  val stalkingThreats = trackers.filter { it.isFollowingThreat }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF050505))
  ) {
    TacticalHeader(
      title = "BLE TRACKERS",
      subtitle = "ANTI-STALKER // AIRTAG / TILE / SMARTTAG",
      rfDensity = "${trackers.size} DETECTED",
      subnet = if (stalkingThreats.isNotEmpty()) "${stalkingThreats.size} FOLLOWING THREATS" else "CLEAR",
      onMenuClick = onOpenDrawer
    )

    if (stalkingThreats.isNotEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0x3333090E))
          .border(1.dp, SignalRed, RoundedCornerShape(8.dp))
          .padding(12.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.Warning, contentDescription = null, tint = SignalRed)
          Column {
            Text(
              text = "STALKING PERSISTENCE ALERT",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = SignalRed
            )
            Text(
              text = "1+ BLE beacon has been detected across multiple time windows nearby.",
              fontFamily = FontFamily.Default,
              fontSize = 10.sp,
              color = Color(0xFFA1A1AA)
            )
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(trackers, key = { it.mac }) { tracker ->
        BleTrackerCard(tracker = tracker)
      }

      if (trackers.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "SCANNING FOR AIRTAGS, TILES & SMARTTAGS...",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = Color(0xFF52525B)
            )
          }
        }
      }
    }
  }
}

@Composable
fun BleTrackerCard(tracker: BleTracker) {
  val isThreat = tracker.isFollowingThreat

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(if (isThreat) Color(0x3333090E) else Color(0xFF0C0E14))
      .border(
        1.dp,
        if (isThreat) SignalRed else Color(0x1AFFFFFF),
        RoundedCornerShape(8.dp)
      )
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
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = if (isThreat) Icons.Default.Warning else Icons.Default.GpsFixed,
            contentDescription = null,
            tint = if (isThreat) SignalRed else AlertAmber,
            modifier = Modifier.size(18.dp)
          )
          Column {
            Text(
              text = tracker.brand.name.replace("_", " "),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = if (isThreat) SignalRed else Color.White
            )
            Text(
              text = tracker.mac,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              color = Color(0xFFA1A1AA)
            )
          }
        }

        SignalQualityMeter(rssi = tracker.rssi)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "SIGHTINGS: ${tracker.sightingCount}",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = if (tracker.sightingCount > 4) AlertAmber else Color(0xFF71717A)
        )
        Text(
          text = if (isThreat) "FOLLOWING TARGET" else "IN RANGE",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (isThreat) SignalRed else RadarCyan
        )
      }
    }
  }
}
