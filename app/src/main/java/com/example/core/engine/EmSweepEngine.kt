package com.example.core.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.hub.ReconHub
import com.example.core.model.EmReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class EmSweepEngine(private val context: Context) : SensorEventListener {

  private val reconHub = ReconHub.getInstance()
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

  private val _emReading = MutableStateFlow(EmReading())
  val emReading: StateFlow<EmReading> = _emReading.asStateFlow()

  private val _history = MutableStateFlow<List<Float>>(emptyList())
  val history: StateFlow<List<Float>> = _history.asStateFlow()

  private val _isSensorAvailable = MutableStateFlow(magnetometer != null)
  val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable.asStateFlow()

  private val recentSamples = ArrayDeque<Float>(50)
  private var baselineFlux = 45.0f

  fun start() {
    magnetometer?.let {
      sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
      reconHub.logMessage("EM", "Magnetometer sensor hook registered")
    }
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  fun recalibrateBaseline() {
    if (recentSamples.isNotEmpty()) {
      val sorted = recentSamples.sorted()
      baselineFlux = sorted[sorted.size / 2]
      reconHub.logMessage("EM", "Recalibrated baseline median to %.1f uT".format(baselineFlux))
    }
  }

  override fun onSensorChanged(event: SensorEvent?) {
    event ?: return
    if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
      val x = event.values[0]
      val y = event.values[1]
      val z = event.values[2]
      val mag = sqrt(x * x + y * y + z * z)

      if (recentSamples.size >= 50) recentSamples.removeFirst()
      recentSamples.addLast(mag)

      if (baselineFlux <= 0f && recentSamples.size >= 10) {
        val sorted = recentSamples.sorted()
        baselineFlux = sorted[sorted.size / 2]
      }

      val delta = mag - baselineFlux
      val isSpike = delta > 25.0f

      val reading = EmReading(
        x = x,
        y = y,
        z = z,
        magnitudeUt = mag,
        baselineUt = baselineFlux,
        deltaUt = delta,
        isSpike = isSpike,
        timestamp = System.currentTimeMillis()
      )
      _emReading.value = reading
      _history.value = recentSamples.toList()
      reconHub.updateEmReading(reading)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
