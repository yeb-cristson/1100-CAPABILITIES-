package com.example.engine

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.core.model.GlintCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

class CameraGlintEngine : ImageAnalysis.Analyzer {

  private val _detectedGlints = MutableStateFlow<List<GlintCandidate>>(emptyList())
  val detectedGlints: StateFlow<List<GlintCandidate>> = _detectedGlints.asStateFlow()

  private val _sensitivityThreshold = MutableStateFlow(240)
  val sensitivityThreshold: StateFlow<Int> = _sensitivityThreshold.asStateFlow()

  private val _ambientLuma = MutableStateFlow(0f)
  val ambientLuma: StateFlow<Float> = _ambientLuma.asStateFlow()

  fun setThreshold(threshold: Int) {
    _sensitivityThreshold.value = threshold.coerceIn(150, 255)
  }

  override fun analyze(image: ImageProxy) {
    val plane = image.planes.firstOrNull()
    if (plane == null) {
      image.close()
      return
    }

    try {
      val buffer: ByteBuffer = plane.buffer
      val width = image.width
      val height = image.height
      val rowStride = plane.rowStride
      val pixelStride = plane.pixelStride

      val threshold = _sensitivityThreshold.value
      val candidates = mutableListOf<GlintCandidate>()
      var totalLuma = 0L
      var sampleCount = 0

      val step = 4
      for (y in 0 until height step step) {
        for (x in 0 until width step step) {
          val index = y * rowStride + x * pixelStride
          if (index < buffer.remaining()) {
            val luma = buffer.get(index).toInt() and 0xFF
            totalLuma += luma
            sampleCount++

            if (luma >= threshold) {
              val relX = x.toFloat() / width
              val relY = y.toFloat() / height

              val tooClose = candidates.any { c ->
                val dx = c.x - relX
                val dy = c.y - relY
                (dx * dx + dy * dy) < 0.0025f
              }

              if (!tooClose && candidates.size < 8) {
                candidates.add(
                  GlintCandidate(
                    x = relX,
                    y = relY,
                    intensity = luma / 255f,
                    clusterSize = 1,
                    confidence = 0.95f,
                    timestamp = System.currentTimeMillis()
                  )
                )
              }
            }
          }
        }
      }

      if (sampleCount > 0) {
        _ambientLuma.value = (totalLuma.toFloat() / sampleCount)
      }
      _detectedGlints.value = candidates
    } catch (_: Exception) {
    } finally {
      image.close()
    }
  }
}
