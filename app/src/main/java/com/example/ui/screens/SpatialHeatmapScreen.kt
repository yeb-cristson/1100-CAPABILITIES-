package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.SpatialDevicePoint
import com.example.core.model.SpatialHeatmapState
import com.example.core.util.DeviceCategory
import com.example.engine.SpatialHeatmapEngine
import com.example.ui.components.RssiSignalBar
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class HeatmapRendererMode {
  D3_INTERACTIVE,
  NATIVE_CANVAS
}

class WebAppInterface(private val onDeviceSelected: (String) -> Unit) {
  @JavascriptInterface
  fun onDeviceSelected(deviceId: String) {
    onDeviceSelected.invoke(deviceId)
  }
}

@Composable
fun SpatialHeatmapScreen(
  engine: SpatialHeatmapEngine,
  onOpenDrawer: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val state by engine.heatmapState.collectAsStateWithLifecycle()
  var rendererMode by remember { mutableStateOf(HeatmapRendererMode.D3_INTERACTIVE) }
  var selectedDevice by remember { mutableStateOf<SpatialDevicePoint?>(null) }
  var showExportDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(DarkBackground)
  ) {
    // 1. Tactical Header with GPS & spatial telemetry
    TacticalHeader(
      title = "M13 // SPATIAL D3 HEATMAP",
      subtitle = "GPS ${String.format("%.4f", state.userLocation.latitude)}, ${String.format("%.4f", state.userLocation.longitude)} | ±${state.userLocation.accuracyM}m",
      rfDensity = "${state.spatialDevices.size} SPATIAL NODES",
      subnet = "DENSITY: ${String.format("%.2f", state.totalDeviceDensity)} D/m²",
      onMenuClick = onOpenDrawer
    )

    // 2. View Mode & Category Filter Ribbon
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(DarkSurface)
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Engine Selector Switcher
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkBackground)
            .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
            .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          ModeSelectorButton(
            label = "D3.JS VECTOR",
            icon = Icons.Default.Language,
            isSelected = rendererMode == HeatmapRendererMode.D3_INTERACTIVE,
            onClick = { rendererMode = HeatmapRendererMode.D3_INTERACTIVE }
          )
          ModeSelectorButton(
            label = "NATIVE CANVAS",
            icon = Icons.Default.Sensors,
            isSelected = rendererMode == HeatmapRendererMode.NATIVE_CANVAS,
            onClick = { rendererMode = HeatmapRendererMode.NATIVE_CANVAS }
          )
        }

        // Export GeoJSON Button
        IconButton(
          onClick = {
            val geoJson = engine.exportGeoJson()
            Toast.makeText(context, "GeoJSON spatial dataset exported (${geoJson.length} bytes)", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DarkBackground)
            .border(1.dp, RadarCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
        ) {
          Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Export GeoJSON",
            tint = RadarCyan,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // Filter Chips
      val filterScroll = rememberScrollState()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(filterScroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        CategoryFilterChip(
          label = "ALL (${state.spatialDevices.size})",
          isSelected = state.selectedFilterCategory == null,
          onClick = { engine.setFilterCategory(null) }
        )
        CategoryFilterChip(
          label = "PHONES",
          isSelected = state.selectedFilterCategory == DeviceCategory.SMARTPHONE,
          color = Color(0xFF00E5FF),
          onClick = { engine.setFilterCategory(DeviceCategory.SMARTPHONE) }
        )
        CategoryFilterChip(
          label = "LAPTOPS",
          isSelected = state.selectedFilterCategory == DeviceCategory.LAPTOP_PC,
          color = Color(0xFF00E676),
          onClick = { engine.setFilterCategory(DeviceCategory.LAPTOP_PC) }
        )
        CategoryFilterChip(
          label = "SPY CAMS",
          isSelected = state.selectedFilterCategory == DeviceCategory.SPY_CAMERA_SURVEILLANCE,
          color = SignalRed,
          onClick = { engine.setFilterCategory(DeviceCategory.SPY_CAMERA_SURVEILLANCE) }
        )
        CategoryFilterChip(
          label = "TRACKERS",
          isSelected = state.selectedFilterCategory == DeviceCategory.TRACKER_BEACON,
          color = AlertAmber,
          onClick = { engine.setFilterCategory(DeviceCategory.TRACKER_BEACON) }
        )
        CategoryFilterChip(
          label = "COVERT BUGS",
          isSelected = state.selectedFilterCategory == DeviceCategory.AUDIO_BUG_TRANSMITTER,
          color = Color(0xFFFF5252),
          onClick = { engine.setFilterCategory(DeviceCategory.AUDIO_BUG_TRANSMITTER) }
        )
        CategoryFilterChip(
          label = "ROUTERS",
          isSelected = state.selectedFilterCategory == DeviceCategory.NETWORK_INFRASTRUCTURE,
          color = Color(0xFFB388FF),
          onClick = { engine.setFilterCategory(DeviceCategory.NETWORK_INFRASTRUCTURE) }
        )
      }
    }

    // 3. Primary Heatmap Viewport (D3.js or Native Compose Canvas)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .background(Color(0xFF050505))
    ) {
      if (rendererMode == HeatmapRendererMode.D3_INTERACTIVE) {
        D3HeatmapWebView(
          engine = engine,
          onDeviceSelected = { id ->
            selectedDevice = state.spatialDevices.find { it.id == id }
          }
        )
      } else {
        NativeCanvasHeatmapView(
          state = state,
          selectedDevice = selectedDevice,
          onSelectDevice = { selectedDevice = it }
        )
      }

      // HUD Compass & Fix overlay
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0xCC0A0E14))
          .border(1.dp, DarkCardBorder, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = "Compass",
            tint = RadarCyan,
            modifier = Modifier.size(12.dp)
          )
          Text(
            text = "${state.userLocation.headingDegrees.toInt()}° HDG | ${state.peakClusterCount} PEAKS",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = RadarCyan
          )
        }
      }
    }

    // 4. Bottom Device Inspector or Spatial Node List
    if (selectedDevice != null) {
      DeviceInspectorCard(
        device = selectedDevice!!,
        onClose = { selectedDevice = null }
      )
    } else {
      SpatialNodeSummaryList(
        devices = state.spatialDevices,
        onSelect = { selectedDevice = it }
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun D3HeatmapWebView(
  engine: SpatialHeatmapEngine,
  onDeviceSelected: (String) -> Unit
) {
  val htmlContent = remember(engine.heatmapState.collectAsStateWithLifecycle().value) {
    engine.generateD3HeatmapHtml()
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        setBackgroundColor(0xFF050505.toInt())

        addJavascriptInterface(WebAppInterface(onDeviceSelected), "AndroidBridge")

        webViewClient = object : WebViewClient() {}
        loadDataWithBaseURL("https://recon.internal/", htmlContent, "text/html", "UTF-8", null)
      }
    },
    update = { webView ->
      webView.loadDataWithBaseURL("https://recon.internal/", htmlContent, "text/html", "UTF-8", null)
    }
  )
}

@Composable
private fun NativeCanvasHeatmapView(
  state: SpatialHeatmapState,
  selectedDevice: SpatialDevicePoint?,
  onSelectDevice: (SpatialDevicePoint) -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "RadarSweepRot"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(8.dp)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = (minOf(size.width, size.height) / 2f) * 0.92f
      val maxRangeMeters = 30f
      val scale = maxRadius / maxRangeMeters

      // 1. Concentric Range Circles
      val rings = listOf(5f, 10f, 15f, 20f, 25f, 30f)
      rings.forEach { rM ->
        val radPx = rM * scale
        drawCircle(
          color = Color(0x2600E5FF),
          radius = radPx,
          center = center,
          style = Stroke(width = 1.dp.toPx())
        )
      }

      // 2. Axes
      drawLine(
        color = Color(0x1A00E5FF),
        start = Offset(center.x - maxRadius, center.y),
        end = Offset(center.x + maxRadius, center.y),
        strokeWidth = 1.dp.toPx()
      )
      drawLine(
        color = Color(0x1A00E5FF),
        start = Offset(center.x, center.y - maxRadius),
        end = Offset(center.x, center.y + maxRadius),
        strokeWidth = 1.dp.toPx()
      )

      // 3. Radar Sweep Line
      val radSweep = Math.toRadians(rotation.toDouble()).toFloat()
      val sweepEnd = Offset(
        x = center.x + maxRadius * cos(radSweep),
        y = center.y + maxRadius * sin(radSweep)
      )
      drawLine(
        color = RadarCyan.copy(alpha = 0.4f),
        start = center,
        end = sweepEnd,
        strokeWidth = 1.5.dp.toPx()
      )

      // 4. Gaussian Density Heatmap Blobs
      state.spatialDevices.forEach { dev ->
        val devX = center.x + (dev.relX * scale)
        val devY = center.y - (dev.relY * scale) // Invert Y
        val blobRadius = (45f - dev.distanceMeters.toFloat()).coerceIn(20f, 65f).dp.toPx()

        val heatColor = when {
          dev.threatScore >= 80 -> SignalRed
          dev.threatScore >= 40 -> AlertAmber
          dev.category == DeviceCategory.SMARTPHONE -> RadarCyan
          dev.category == DeviceCategory.LAPTOP_PC -> Color(0xFF00E676)
          else -> Color(0xFF00E5FF)
        }

        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              heatColor.copy(alpha = 0.55f),
              heatColor.copy(alpha = 0.15f),
              Color.Transparent
            ),
            center = Offset(devX, devY),
            radius = blobRadius
          ),
          radius = blobRadius,
          center = Offset(devX, devY)
        )
      }

      // 5. Device Node Glyphs
      state.spatialDevices.forEach { dev ->
        val devX = center.x + (dev.relX * scale)
        val devY = center.y - (dev.relY * scale)
        val isSelected = selectedDevice?.id == dev.id

        val nodeColor = when {
          dev.threatScore >= 80 -> SignalRed
          dev.threatScore >= 40 -> AlertAmber
          dev.category == DeviceCategory.SMARTPHONE -> RadarCyan
          dev.category == DeviceCategory.LAPTOP_PC -> Color(0xFF00E676)
          else -> Color(0xFF00E5FF)
        }

        drawCircle(
          color = if (isSelected) Color.White else nodeColor,
          radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
          center = Offset(devX, devY)
        )

        drawCircle(
          color = nodeColor,
          radius = if (isSelected) 10.dp.toPx() else 7.dp.toPx(),
          center = Offset(devX, devY),
          style = Stroke(width = 1.dp.toPx())
        )
      }

      // 6. User Position Pulse & Center
      drawCircle(
        color = RadarCyan.copy(alpha = 0.25f),
        radius = 12.dp.toPx(),
        center = center
      )
      drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = center
      )
    }
  }
}

@Composable
private fun ModeSelectorButton(
  label: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) Color(0x3300E5FF) else Color.Transparent)
      .border(1.dp, if (isSelected) RadarCyan else Color.Transparent, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (isSelected) RadarCyan else TextMuted,
      modifier = Modifier.size(12.dp)
    )
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) Color.White else TextMuted
    )
  }
}

@Composable
private fun CategoryFilterChip(
  label: String,
  isSelected: Boolean,
  color: Color = RadarCyan,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) color.copy(alpha = 0.2f) else DarkBackground)
      .border(1.dp, if (isSelected) color else DarkCardBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) color else TextMuted
    )
  }
}

@Composable
private fun DeviceInspectorCard(
  device: SpatialDevicePoint,
  onClose: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
      .background(DarkSurface)
      .border(1.dp, if (device.threatScore >= 75) SignalRed else RadarCyan, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
      .padding(12.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
              .clip(CircleShape)
              .background(if (device.threatScore >= 75) SignalRed else RadarCyan)
          )
          Text(
            text = "[${device.categoryLabel}] ${device.name}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            color = Color.White
          )
        }
        IconButton(
          onClick = onClose,
          modifier = Modifier.size(24.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        InspectorStatBox(
          label = "DISTANCE",
          value = "${String.format("%.1f", device.distanceMeters)}m",
          modifier = Modifier.weight(1f)
        )
        InspectorStatBox(
          label = "BEARING",
          value = "${device.bearingDeg.toInt()}°",
          modifier = Modifier.weight(1f)
        )
        InspectorStatBox(
          label = "RSSI",
          value = "${device.rssi} dBm",
          modifier = Modifier.weight(1f)
        )
        InspectorStatBox(
          label = "THREAT",
          value = "${device.threatScore}/100",
          accentColor = if (device.threatScore >= 75) SignalRed else RadarCyan,
          modifier = Modifier.weight(1f)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "IDENTIFIER: ${device.identifier} | ${device.vendor}",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = TextSecondary
        )
        Text(
          text = if (device.isOfflineOrUnassociated) "OFFLINE / UNASSOCIATED" else "ASSOCIATED",
          fontFamily = FontFamily.Monospace,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.Bold,
          color = if (device.isOfflineOrUnassociated) RadarCyan else Color(0xFF00E676)
        )
      }
    }
  }
}

@Composable
private fun InspectorStatBox(
  label: String,
  value: String,
  accentColor: Color = Color.White,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(4.dp))
      .background(DarkBackground)
      .border(1.dp, DarkCardBorder, RoundedCornerShape(4.dp))
      .padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 7.sp,
      color = TextMuted
    )
    Text(
      text = value,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = accentColor
    )
  }
}

@Composable
private fun SpatialNodeSummaryList(
  devices: List<SpatialDevicePoint>,
  onSelect: (SpatialDevicePoint) -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(140.dp)
      .background(DarkSurface)
      .border(1.dp, DarkCardBorder)
      .padding(8.dp)
  ) {
    if (devices.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "ACQUIRING SPATIAL RF EMITTERS...",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = TextMuted
        )
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        items(devices) { dev ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(DarkBackground)
              .border(1.dp, if (dev.threatScore >= 75) SignalRed.copy(alpha = 0.6f) else DarkCardBorder, RoundedCornerShape(4.dp))
              .clickable { onSelect(dev) }
              .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = dev.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
              )
              Text(
                text = "${dev.categoryLabel} • ${dev.vendor} • ${String.format("%.1f", dev.distanceMeters)}m",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                color = TextSecondary,
                maxLines = 1
              )
            }
            RssiSignalBar(rssi = dev.rssi)
          }
        }
      }
    }
  }
}
