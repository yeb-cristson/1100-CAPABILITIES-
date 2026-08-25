package com.example.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.core.model.AcousticSpectrum
import com.example.core.util.Fft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AcousticEngine {

  private val scope = CoroutineScope(Dispatchers.Default)
  private var recordingJob: Job? = null

  private val _spectrum = MutableStateFlow(
    AcousticSpectrum(
      peakHz = 0,
      peakMagnitude = 0f,
      ultrasonicEnergy = 0f,
      isUltrasonicActive = false,
      frequencyBins = FloatArray(64)
    )
  )
  val spectrum: StateFlow<AcousticSpectrum> = _spectrum.asStateFlow()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _statusMessage = MutableStateFlow("Acoustic monitor idle")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private val sampleRate = 44100
  private val fftSize = 1024

  @SuppressLint("MissingPermission")
  fun start() {
    if (_isRecording.value) return
    _isRecording.value = true
    _statusMessage.value = "Sampling 44.1 kHz PCM audio..."

    recordingJob = scope.launch {
      var recorder: AudioRecord? = null
      try {
        val minBufferSize = AudioRecord.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (fftSize * 2).coerceAtLeast(minBufferSize)

        recorder = AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
          _statusMessage.value = "Microphone failed to initialize"
          _isRecording.value = false
          return@launch
        }

        recorder.startRecording()
        val audioBuffer = ShortArray(fftSize)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        while (_isRecording.value) {
          val read = recorder.read(audioBuffer, 0, fftSize)
          if (read > 0) {
            for (i in 0 until fftSize) {
              // Hanning window
              val window = 0.5f * (1f - Math.cos(2.0 * Math.PI * i / (fftSize - 1)).toFloat())
              real[i] = (audioBuffer[i] / 32768.0f) * window
              imag[i] = 0f
            }

            Fft.transform(real, imag)

            val binCount = 64
            val step = (fftSize / 2) / binCount
            val displayBins = FloatArray(binCount)
            var maxMag = 0f
            var peakIndex = 0

            var ultrasonicSum = 0f
            var ultrasonicCount = 0

            for (b in 0 until binCount) {
              var sum = 0f
              for (k in 0 until step) {
                val idx = b * step + k
                val mag = Math.sqrt((real[idx] * real[idx] + imag[idx] * imag[idx]).toDouble()).toFloat()
                sum += mag
                if (mag > maxMag) {
                  maxMag = mag
                  peakIndex = idx
                }
              }
              val avg = (sum / step) * 4f
              displayBins[b] = avg

              val freqCenter = (b * step) * (sampleRate.toFloat() / fftSize)
              if (freqCenter >= 17000f) {
                ultrasonicSum += avg
                ultrasonicCount++
              }
            }

            val peakHz = ((peakIndex * sampleRate) / fftSize)
            val ultrasonicAvg = if (ultrasonicCount > 0) ultrasonicSum / ultrasonicCount else 0f
            val isUltrasonic = ultrasonicAvg > 0.040f

            _spectrum.value = AcousticSpectrum(
              peakHz = peakHz,
              peakMagnitude = maxMag,
              ultrasonicEnergy = ultrasonicAvg,
              isUltrasonicActive = isUltrasonic,
              frequencyBins = displayBins
            )
          }
        }
      } catch (e: Exception) {
        _statusMessage.value = "Audio error: ${e.localizedMessage}"
      } finally {
        try {
          recorder?.stop()
          recorder?.release()
        } catch (_: Exception) {}
        _isRecording.value = false
      }
    }
  }

  fun stop() {
    _isRecording.value = false
    recordingJob?.cancel()
    _statusMessage.value = "Acoustic monitor standby"
  }
}
