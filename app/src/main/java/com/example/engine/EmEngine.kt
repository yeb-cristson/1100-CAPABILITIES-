package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.model.EmReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class EmEngine(private val context: Context) : SensorEventListener {

  private val sensorManager by lazy {
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  }

  private val _reading = MutableStateFlow(EmReading())
  val reading: StateFlow<EmReading> = _reading.asStateFlow()

  private val _isMonitoring = MutableStateFlow(false)
  val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

  private val _statusMessage = MutableStateFlow("EM sensor idle")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private var baselineEma = 45f // typical earth magnetic field ~45 uT
  private var isBaselineCalibrated = false
  private var sampleCount = 0

  fun start() {
    val sm = sensorManager ?: return
    val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    if (magSensor != null) {
      sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI)
      _isMonitoring.value = true
      _statusMessage.value = "Active magnetometer sensor reading at 60 Hz"
    } else {
      _statusMessage.value = "Hardware magnetometer not detected on device"
    }
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
    _isMonitoring.value = false
    _statusMessage.value = "EM sensor standby"
  }

  fun resetBaseline() {
    isBaselineCalibrated = false
    sampleCount = 0
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
      val x = event.values[0]
      val y = event.values[1]
      val z = event.values[2]
      val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

      sampleCount++
      if (!isBaselineCalibrated) {
        if (sampleCount == 1) {
          baselineEma = magnitude
        } else {
          baselineEma = baselineEma * 0.9f + magnitude * 0.1f
        }
        if (sampleCount > 30) isBaselineCalibrated = true
      } else {
        // Slow EMA tracking of ambient baseline
        baselineEma = baselineEma * 0.995f + magnitude * 0.005f
      }

      val delta = magnitude - baselineEma
      val isSpike = (delta > 15f || magnitude > 85f)

      _reading.value = EmReading(
        x = x,
        y = y,
        z = z,
        magnitudeUt = magnitude,
        baselineUt = baselineEma,
        deltaUt = delta,
        isSpike = isSpike,
        timestamp = System.currentTimeMillis()
      )
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
