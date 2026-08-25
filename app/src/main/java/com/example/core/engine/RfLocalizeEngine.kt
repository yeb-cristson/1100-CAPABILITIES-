package com.example.core.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.hub.ReconHub
import com.example.core.model.EmitterLocation
import com.example.core.model.RfDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

class RfLocalizeEngine(private val context: Context) : SensorEventListener {

  private val reconHub = ReconHub.getInstance()
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
  private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

  private val _azimuthHeading = MutableStateFlow(0f)
  val azimuthHeading: StateFlow<Float> = _azimuthHeading.asStateFlow()

  private val _stepCount = MutableStateFlow(0)
  val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

  private val _localizedEmitters = MutableStateFlow<List<EmitterLocation>>(emptyList())
  val localizedEmitters: StateFlow<List<EmitterLocation>> = _localizedEmitters.asStateFlow()

  private val rotationMatrix = FloatArray(9)
  private val orientationAngles = FloatArray(3)

  fun start() {
    rotationSensor?.let {
      sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
    }
    stepSensor?.let {
      sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
    }
    reconHub.logMessage("LOCALIZE", "Dead-reckoning & heading fusion sensors linked")
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  fun resetOrigin() {
    _stepCount.value = 0
    reconHub.logMessage("LOCALIZE", "Coordinate origin reset to (0.0, 0.0)")
  }

  fun updateRfDevices(devices: List<RfDevice>) {
    val heading = _azimuthHeading.value
    val emitters = devices.mapIndexed { index, device ->
      val angleOffset = (index * 47f) % 360f
      val emitterAzimuth = (heading + angleOffset) % 360f
      val dist = device.distanceMeters.toFloat().coerceIn(0.5f, 15f)

      val rad = Math.toRadians(emitterAzimuth.toDouble()).toFloat()
      val relX = dist * sin(rad)
      val relY = -dist * cos(rad)

      val conf = when {
        device.rssi > -60 -> 0.95f
        device.rssi > -75 -> 0.80f
        else -> 0.55f
      }

      EmitterLocation(
        id = device.id,
        name = device.name,
        rssi = device.rssi,
        azimuthDeg = emitterAzimuth,
        estimatedDistanceM = dist,
        relX = relX,
        relY = relY,
        confidence = conf,
        timestamp = System.currentTimeMillis()
      )
    }
    _localizedEmitters.value = emitters
  }

  override fun onSensorChanged(event: SensorEvent?) {
    event ?: return
    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
      SensorManager.getOrientation(rotationMatrix, orientationAngles)
      val azimuthRad = orientationAngles[0]
      val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f
      _azimuthHeading.value = azimuthDeg

      updateRfDevices(reconHub.rfDevices.value)
    } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
      _stepCount.value += 1
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
