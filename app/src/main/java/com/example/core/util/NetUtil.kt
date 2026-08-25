package com.example.core.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {

  fun getLocalIpv4Address(): String? {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      while (interfaces.hasMoreElements()) {
        val intf = interfaces.nextElement()
        if (intf.isLoopback || !intf.isUp) continue
        val addresses = intf.inetAddresses
        while (addresses.hasMoreElements()) {
          val addr = addresses.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val hostAddress = addr.hostAddress
            if (hostAddress != null && !hostAddress.startsWith("127.")) {
              return hostAddress
            }
          }
        }
      }
    } catch (_: Exception) {
    }
    return null
  }

  fun getSubnet24Range(ip: String): List<String> {
    val parts = ip.split(".")
    if (parts.size != 4) return emptyList()
    val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
    val list = ArrayList<String>(254)
    for (i in 1..254) {
      list.add("$prefix.$i")
    }
    return list
  }

  fun guessVendorFromBanner(banner: String): String {
    val lower = banner.lowercase()
    return when {
      "hikvision" in lower || "hik-server" in lower || "dvrip-web" in lower -> "Hikvision IP Camera"
      "dahua" in lower || "dh-rtsp" in lower -> "Dahua Tech Camera"
      "axis" in lower -> "Axis Communications"
      "foscam" in lower -> "Foscam IP Camera"
      "reolink" in lower -> "Reolink Surveillance"
      "avtech" in lower -> "AVTECH CCTV"
      "hipcam" in lower -> "HiSilicon / Hipcam"
      "tplink" in lower || "tapo" in lower -> "TP-Link Tapo"
      "amcrest" in lower -> "Amcrest Camera"
      "sonoff" in lower || "esp8266" in lower || "espressif" in lower -> "Espressif IoT Device"
      "openwrt" in lower || "dd-wrt" in lower -> "OpenWrt Router"
      "mikrotik" in lower || "routeros" in lower -> "MikroTik Router"
      "apache" in lower -> "Apache Web Server"
      "nginx" in lower -> "Nginx Server"
      "lighttpd" in lower -> "Lighttpd Embedded Server"
      "rtsp/1.0" in lower -> "Generic RTSP Stream Server"
      else -> "Unknown Network Device"
    }
  }
}
