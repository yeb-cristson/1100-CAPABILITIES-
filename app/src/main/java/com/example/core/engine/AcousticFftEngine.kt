package com.example.core.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.core.hub.ReconHub
import com.example.core.model.AcousticSpectrum
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AcousticFftEngine {

  private val reconHub = ReconHub.getInstance()
  private val _spectrum = MutableStateFlow(AcousticSpectrum())
  val spectrum: StateFlow<AcousticSpectrum> = _spectrum.asStateFlow()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private var recordJob: Job? = null
  private var audioRecord: AudioRecord? = null

  @SuppressLint("MissingPermission")
  fun start(scope: CoroutineScope) {
    if (_isRecording.value) return
    _isRecording.value = true
    reconHub.logMessage("AUDIO", "Radix-2 FFT Acoustic spectrum sampler active")

    recordJob = scope.launch(Dispatchers.IO) {
      val sampleRate = 44100
      val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      ).coerceAtLeast(2048)

      try {
        audioRecord = AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize
        )

        audioRecord?.startRecording()
        val audioBuffer = ShortArray(1024)

        while (isActive && _isRecording.value) {
          val read = audioRecord?.read(audioBuffer, 0, 1024) ?: 0
          if (read > 0) {
            val real = FloatArray(1024)
            val imag = FloatArray(1024)

            for (i in 0 until 1024) {
              val window = 0.5f * (1f - cos(2.0 * Math.PI * i / 1023.0).toFloat())
              real[i] = (audioBuffer[i] / 32768.0f) * window
              imag[i] = 0f
            }

            fft(real, imag)

            val bins = FloatArray(64)
            var maxMag = 0f
            var peakBin = 0

            for (b in 0 until 64) {
              var binSum = 0f
              for (k in 0 until 8) {
                val idx = b * 8 + k
                val mag = sqrt(real[idx] * real[idx] + imag[idx] * imag[idx])
                binSum += mag
                if (mag > maxMag) {
                  maxMag = mag
                  peakBin = idx
                }
              }
              bins[b] = (binSum / 8f).coerceIn(0f, 1f)
            }

            val peakHz = (peakBin * (sampleRate / 1024.0)).toInt()
            var ultrasonicSum = 0f
            for (b in 40 until 64) {
              ultrasonicSum += bins[b]
            }
            val isUltrasonic = ultrasonicSum > 0.45f || peakHz >= 17000

            val result = AcousticSpectrum(
              peakHz = peakHz,
              peakMagnitude = maxMag,
              ultrasonicEnergy = ultrasonicSum,
              isUltrasonicActive = isUltrasonic,
              frequencyBins = bins,
              timestamp = System.currentTimeMillis()
            )

            _spectrum.value = result
            reconHub.updateAcoustic(result)
          }
          delay(80)
        }
      } catch (e: Exception) {
        reconHub.logMessage("AUDIO", "Mic capture error: ${e.message}")
      } finally {
        try {
          audioRecord?.stop()
          audioRecord?.release()
        } catch (_: Exception) {}
        _isRecording.value = false
      }
    }
  }

  fun stop() {
    _isRecording.value = false
    recordJob?.cancel()
    try {
      audioRecord?.stop()
      audioRecord?.release()
    } catch (_: Exception) {}
  }

  private fun fft(real: FloatArray, imag: FloatArray) {
    val n = real.size
    var j = 0
    for (i in 0 until n - 1) {
      if (i < j) {
        val tempR = real[i]; real[i] = real[j]; real[j] = tempR
        val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
      }
      var k = n / 2
      while (k <= j) {
        j -= k
        k /= 2
      }
      j += k
    }

    var l = 1
    while (l < n) {
      val step = l * 2
      val theta = -Math.PI / l
      for (m in 0 until l) {
        val wR = cos(m * theta).toFloat()
        val wI = sin(m * theta).toFloat()
        var i = m
        while (i < n) {
          val partner = i + l
          val tR = wR * real[partner] - wI * imag[partner]
          val tI = wR * imag[partner] + wI * real[partner]
          real[partner] = real[i] - tR
          imag[partner] = imag[i] - tI
          real[i] += tR
          imag[i] += tI
          i += step
        }
      }
      l = step
    }
  }
}
