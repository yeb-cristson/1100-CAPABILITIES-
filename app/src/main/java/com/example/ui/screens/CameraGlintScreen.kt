package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.CameraGlintEngine
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.SignalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThreatCritical
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraGlintScreen(
  engine: CameraGlintEngine,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val detectedGlints by engine.detectedGlints.collectAsState()
  val threshold by engine.sensitivityThreshold.collectAsState()
  val ambientLuma by engine.ambientLuma.collectAsState()

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
              text = "M6 // IR OPTICAL GLINT DETECTOR",
              color = SignalRed,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "CAMERAX SPECULAR PINPOINT REFLECTION FILTER",
              color = TextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }

          Text(
            text = "${detectedGlints.size} GLINTS",
            color = if (detectedGlints.isNotEmpty()) ThreatCritical else RadarCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "SENSITIVITY THRESHOLD: $threshold | LUMA: %.1f".format(ambientLuma),
          color = TextSecondary,
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )

        Slider(
          value = threshold.toFloat(),
          onValueChange = { engine.setThreshold(it.toInt()) },
          valueRange = 160f..255f,
          colors = SliderDefaults.colors(
            thumbColor = SignalRed,
            activeTrackColor = SignalRed,
            inactiveTrackColor = DarkCardBorder
          )
        )
      }
    }

    // Camera Preview Viewport with HUD Overlay
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(380.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(DarkSurface)
          .border(
            width = 1.5.dp,
            color = if (detectedGlints.isNotEmpty()) ThreatCritical else DarkCardBorder,
            shape = RoundedCornerShape(10.dp)
          )
      ) {
        AndroidView(
          factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              val cameraProvider = cameraProviderFuture.get()
              val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
              }

              val executor = Executors.newSingleThreadExecutor()
              val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

              imageAnalysis.setAnalyzer(executor, engine)

              try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  CameraSelector.DEFAULT_BACK_CAMERA,
                  preview,
                  imageAnalysis
                )
              } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))
            previewView
          },
          modifier = Modifier.fillMaxSize()
        )

        // Overlay Glint Crosshairs
        Canvas(modifier = Modifier.fillMaxSize()) {
          detectedGlints.forEach { glint ->
            val px = glint.x * size.width
            val py = glint.y * size.height
            drawCircle(
              color = SignalRed,
              radius = 16.dp.toPx(),
              center = Offset(px, py),
              style = Stroke(2.dp.toPx())
            )
            drawCircle(
              color = SignalRed,
              radius = 4.dp.toPx(),
              center = Offset(px, py)
            )
          }
        }

        if (detectedGlints.isNotEmpty()) {
          Box(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 10.dp)
              .background(SignalRed, RoundedCornerShape(4.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "OPTICAL LENS REFLECTION DETECTED",
              color = TextPrimary,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            )
          }
        }
      }
    }
  }
}
