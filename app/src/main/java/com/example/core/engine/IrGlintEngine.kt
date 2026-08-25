package com.example.core.engine

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.core.hub.ReconHub
import com.example.core.model.GlintPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IrGlintEngine : ImageAnalysis.Analyzer {

  private val reconHub = ReconHub.getInstance()
  private val _detectedGlints = MutableStateFlow<List<GlintPoint>>(emptyList())
  val detectedGlints: StateFlow<List<GlintPoint>> = _detectedGlints.asStateFlow()

  private val _sensitivityThreshold = MutableStateFlow(240)
  val sensitivityThreshold: StateFlow<Int> = _sensitivityThreshold.asStateFlow()

  fun setThreshold(value: Int) {
    _sensitivityThreshold.value = value.coerceIn(180, 255)
  }

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }

    try {
      val planes = mediaImage.planes
      if (planes.isEmpty()) {
        imageProxy.close()
        return
      }

      val buffer = planes[0].buffer
      val width = mediaImage.width
      val height = mediaImage.height
      val rowStride = planes[0].rowStride
      val pixelStride = planes[0].pixelStride

      val threshold = _sensitivityThreshold.value
      val candidatePoints = mutableListOf<GlintPoint>()

      val step = 12
      for (y in 0 until height step step) {
        for (x in 0 until width step step) {
          val index = y * rowStride + x * pixelStride
          if (index < buffer.remaining()) {
            val luminance = buffer.get(index).toInt() and 0xFF
            if (luminance >= threshold) {
              candidatePoints.add(
                GlintPoint(
                  x = x.toFloat() / width,
                  y = y.toFloat() / height,
                  intensity = luminance.toFloat(),
                  clusterSize = 1,
                  confidence = (luminance - threshold) / (255f - threshold + 1f),
                  timestamp = System.currentTimeMillis()
                )
              )
            }
          }
        }
      }

      val clusters = candidatePoints.take(8)
      _detectedGlints.value = clusters
      reconHub.updateGlints(clusters)
    } catch (_: Exception) {
    } finally {
      imageProxy.close()
    }
  }
}
