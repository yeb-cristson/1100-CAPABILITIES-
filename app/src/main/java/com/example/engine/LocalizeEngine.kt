package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.model.EmitterLocation
import com.example.core.model.RfDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

class LocalizeEngine(
  private val context: Context,
  private val airspaceEngine: AirspaceEngine
) : SensorEventListener {

  private val sensorManager: SensorManager? by lazy {
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  }

  private val _headingDeg = MutableStateFlow(0f)
  val headingDeg: StateFlow<Float> = _headingDeg.asStateFlow()

  private val _emitters = MutableStateFlow<List<EmitterLocation>>(emptyList())
  val emitters: StateFlow<List<EmitterLocation>> = _emitters.asStateFlow()

  private val rotationMatrix = FloatArray(9)
  private val orientationAngles = FloatArray(3)

  fun start() {
    val sm = sensorManager ?: return
    val rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (rotSensor != null) {
      sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_UI)
    }
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
      SensorManager.getOrientation(rotationMatrix, orientationAngles)

      var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
      if (azimuthDeg < 0) azimuthDeg += 360f

      _headingDeg.value = azimuthDeg
      recomputePositions(azimuthDeg, airspaceEngine.devices.value)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

  fun updateEmittersFromDevices(devices: List<RfDevice>) {
    recomputePositions(_headingDeg.value, devices)
  }

  private fun recomputePositions(heading: Float, devices: List<RfDevice>) {
    val mapped = devices.take(16).mapIndexed { index, dev ->
      val baseAngleDeg = (index * (360f / devices.size.coerceAtLeast(1)))
      val relativeAngleDeg = (baseAngleDeg - heading + 360f) % 360f
      val rad = Math.toRadians(relativeAngleDeg.toDouble())

      val normDist = (dev.distanceMeters / 15.0).coerceIn(0.15, 0.95)
      val x = (sin(rad) * normDist).toFloat()
      val y = (-cos(rad) * normDist).toFloat()

      EmitterLocation(
        id = dev.id,
        name = dev.name.ifBlank { dev.id.take(8) },
        rssi = dev.rssi,
        azimuthDeg = relativeAngleDeg,
        estimatedDistanceM = dev.distanceMeters.toFloat(),
        relX = x,
        relY = y
      )
    }
    _emitters.value = mapped
  }
}
