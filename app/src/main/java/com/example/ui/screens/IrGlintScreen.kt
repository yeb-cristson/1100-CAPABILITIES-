package com.example.ui.screens

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.core.engine.IrGlintEngine
import com.example.core.hub.ReconHub
import com.example.core.model.GlintPoint
import com.example.ui.components.TacticalHeader
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import java.util.concurrent.Executors

@Composable
fun IrGlintScreen(
  glintEngine: IrGlintEngine,
  onOpenDrawer: () -> Unit
) {
  val reconHub = ReconHub.getInstance()
  val glints by glintEngine.detectedGlints.collectAsState()
  val threshold by glintEngine.sensitivityThreshold.collectAsState()

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var torchEnabled by remember { mutableStateOf(false) }
  var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF050505))
  ) {
    TacticalHeader(
      title = "IR GLINT DETECTOR",
      subtitle = "OPTICAL RETROREFLECTION // HIGH-LUMA MASK",
      rfDensity = "${glints.size} REFLECTIONS",
      subnet = if (glints.isNotEmpty()) "LENS GLINT CANDIDATE" else "NO REFLECTIONS",
      onMenuClick = onOpenDrawer
    )

    // Camera viewfinder with overlay
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(14.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color.Black)
        .border(1.dp, if (glints.isNotEmpty()) SignalRed else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
    ) {
      AndroidView(
        factory = { ctx ->
          val previewView = PreviewView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
          }

          val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
          cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
              it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
              .also {
                it.setAnalyzer(Executors.newSingleThreadExecutor(), glintEngine)
              }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
              cameraProvider.unbindAll()
              val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
              )
              cameraControl = camera.cameraControl
            } catch (_: Exception) {}
          }, ContextCompat.getMainExecutor(ctx))

          previewView
        },
        modifier = Modifier.fillMaxSize()
      )

      // Tactical Glint Target Overlay
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        glints.forEach { pt ->
          val cx = pt.x * w
          val cy = pt.y * h

          drawCircle(
            color = SignalRed,
            radius = 16.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(2.dp.toPx())
          )
          drawCircle(
            color = SignalRed.copy(alpha = 0.4f),
            radius = 28.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(1.dp.toPx())
          )
          drawLine(
            color = SignalRed,
            start = Offset(cx - 24.dp.toPx(), cy),
            end = Offset(cx + 24.dp.toPx(), cy),
            strokeWidth = 1.dp.toPx()
          )
          drawLine(
            color = SignalRed,
            start = Offset(cx, cy - 24.dp.toPx()),
            end = Offset(cx, cy + 24.dp.toPx()),
            strokeWidth = 1.dp.toPx()
          )
        }
      }

      // Live Badge
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(12.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0xCC000000))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = if (glints.isNotEmpty()) "CRITICAL: ${glints.size} LENS GLINTS" else "SCANNING FOR PINHOLE LENSES",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (glints.isNotEmpty()) SignalRed else RadarCyan
        )
      }
    }

    // Controls Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0A0A0A))
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LUMA SENSITIVITY THRESHOLD: $threshold",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = Color(0xFF71717A)
        )
        Slider(
          value = threshold.toFloat(),
          onValueChange = { glintEngine.setThreshold(it.toInt()) },
          valueRange = 180f..255f,
          colors = SliderDefaults.colors(thumbColor = RadarCyan, activeTrackColor = RadarCyan)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      IconButton(
        onClick = {
          torchEnabled = !torchEnabled
          cameraControl?.enableTorch(torchEnabled)
        },
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (torchEnabled) AlertAmber else Color(0xFF1A1A1A))
      ) {
        Icon(
          imageVector = if (torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
          contentDescription = "Toggle IR Illuminator Flashlight",
          tint = if (torchEnabled) Color.Black else Color.White
        )
      }
    }
  }
}
