package com.example.core.util

import kotlin.math.pow

object RfMath {

  /**
   * Log-distance path loss model:
   * d = 10 ^ ((TxPower - RSSI) / (10 * n))
   * n = path loss exponent (2.0 free space, 2.7 to 3.5 indoor)
   * TxPower default at 1m is roughly -59 dBm for BLE, -45 dBm for WiFi
   */
  fun calculateDistance(rssi: Int, txPower: Int = -59, pathLossExp: Double = 2.8): Double {
    if (rssi == 0) return -1.0
    val ratio = (txPower - rssi) / (10.0 * pathLossExp)
    val distance = 10.0.pow(ratio)
    return String.format("%.2f", distance).toDoubleOrNull() ?: distance
  }

  fun calculateDistanceMeters(rssi: Int, freqMhz: Int = 2400): Double {
    val txPower = if (freqMhz in 2400..2500) -55 else -45
    return calculateDistance(rssi, txPower, 2.7)
  }

  fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02X".format(it) }
  }

  fun frequencyToChannel(freqMhz: Int): Int = wifiFrequencyToChannel(freqMhz)

  fun wifiFrequencyToChannel(freqMhz: Int): Int {
    return when {
      freqMhz in 2412..2484 -> {
        if (freqMhz == 2484) 14 else (freqMhz - 2407) / 5
      }
      freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
      freqMhz in 5955..7115 -> (freqMhz - 5950) / 5 // Wi-Fi 6E
      else -> 0
    }
  }

  fun getBandLabel(freqMhz: Int): String {
    return when {
      freqMhz in 2400..2500 -> "2.4 GHz"
      freqMhz in 5000..5900 -> "5 GHz"
      freqMhz in 5925..7125 -> "6 GHz"
      else -> "RF"
    }
  }

  fun getSignalQualityPercent(rssi: Int): Int {
    return when {
      rssi <= -100 -> 0
      rssi >= -50 -> 100
      else -> 2 * (rssi + 100)
    }.coerceIn(0, 100)
  }
}
