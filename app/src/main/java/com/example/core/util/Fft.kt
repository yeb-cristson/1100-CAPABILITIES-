package com.example.core.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Fft {

  /**
   * Performs an in-place Radix-2 Decimation-In-Time Fast Fourier Transform.
   * real and imag arrays must have a length that is a power of 2.
   */
  fun transform(real: FloatArray, imag: FloatArray) {
    val n = real.size
    require(n == imag.size) { "Real and Imag arrays must be identical size" }
    require((n and (n - 1)) == 0) { "Size must be a power of 2" }

    // Bit-reversal permutation
    var j = 0
    for (i in 0 until n - 1) {
      if (i < j) {
        val tempR = real[i]
        real[i] = real[j]
        real[j] = tempR

        val tempI = imag[i]
        imag[i] = imag[j]
        imag[j] = tempI
      }
      var k = n shr 1
      while (k in 1..j) {
        j -= k
        k = k shr 1
      }
      j += k
    }

    // Cooley-Tukey Radix-2 loop
    var len = 2
    while (len <= n) {
      val half = len shr 1
      val angle = (-2.0 * PI / len).toFloat()
      val wStepR = cos(angle.toDouble()).toFloat()
      val wStepI = sin(angle.toDouble()).toFloat()

      var i = 0
      while (i < n) {
        var wR = 1.0f
        var wI = 0.0f
        for (k in 0 until half) {
          val uR = real[i + k]
          val uI = imag[i + k]
          val vR = real[i + k + half] * wR - imag[i + k + half] * wI
          val vI = real[i + k + half] * wI + imag[i + k + half] * wR

          real[i + k] = uR + vR
          imag[i + k] = uI + vI
          real[i + k + half] = uR - vR
          imag[i + k + half] = uI - vI

          val nextWR = wR * wStepR - wI * wStepI
          val nextWI = wR * wStepI + wI * wStepR
          wR = nextWR
          wI = nextWI
        }
        i += len
      }
      len = len shl 1
    }
  }

  /**
   * Computes magnitude array sqrt(re^2 + im^2) for the first N/2 frequency bins.
   */
  fun calculateMagnitudes(real: FloatArray, imag: FloatArray): FloatArray {
    val half = real.size / 2
    val magnitudes = FloatArray(half)
    for (i in 0 until half) {
      magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
    }
    return magnitudes
  }
}
