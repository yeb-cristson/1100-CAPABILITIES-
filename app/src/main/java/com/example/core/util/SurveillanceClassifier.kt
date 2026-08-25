package com.example.core.util

import java.util.Locale

enum class DeviceCategory {
  SMARTPHONE,
  LAPTOP_PC,
  SPY_CAMERA_SURVEILLANCE,
  DRONE_AERIAL,
  AUDIO_BUG_TRANSMITTER,
  TRACKER_BEACON,
  SMART_WEARABLE,
  NETWORK_INFRASTRUCTURE,
  UNKNOWN_RF_EMITTER
}

data class ClassificationResult(
  val category: DeviceCategory,
  val categoryLabel: String,
  val vendor: String,
  val confidence: Float, // 0.0 to 1.0
  val threatScore: Int,   // 0 to 100
  val details: String,
  val isOfflineOrUnassociated: Boolean = true
)

object SurveillanceClassifier {

  // OUI database for high-accuracy hardware identification
  private val OUI_MAP = mapOf(
    // Apple Devices (iPhones, MacBooks, iPads, AirTags)
    "00:17:F2" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "00:1E:52" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "00:26:BB" to ("Apple, Inc." to DeviceCategory.LAPTOP_PC),
    "3C:D0:F8" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "70:3E:AC" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "AC:BC:32" to ("Apple, Inc." to DeviceCategory.LAPTOP_PC),
    "F0:18:98" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "F4:37:B7" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "00:88:65" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),
    "14:7D:DA" to ("Apple, Inc." to DeviceCategory.SMARTPHONE),

    // Samsung Mobile & Appliances
    "00:12:47" to ("Samsung Electronics" to DeviceCategory.SMARTPHONE),
    "00:16:32" to ("Samsung Electronics" to DeviceCategory.SMARTPHONE),
    "00:21:D1" to ("Samsung Electronics" to DeviceCategory.SMARTPHONE),
    "50:01:D9" to ("Samsung Galaxy" to DeviceCategory.SMARTPHONE),
    "94:65:2D" to ("Samsung Galaxy" to DeviceCategory.SMARTPHONE),
    "CC:07:AB" to ("Samsung Electronics" to DeviceCategory.SMARTPHONE),
    "DC:71:44" to ("Samsung Galaxy" to DeviceCategory.SMARTPHONE),

    // Google Pixel & IoT
    "3C:5A:B4" to ("Google Pixel" to DeviceCategory.SMARTPHONE),
    "94:EB:CD" to ("Google, LLC" to DeviceCategory.SMARTPHONE),
    "F4:F5:D8" to ("Google Nest / Pixel" to DeviceCategory.SMARTPHONE),
    "54:60:09" to ("Google, LLC" to DeviceCategory.SMARTPHONE),

    // Laptops & PC Chipsets (Intel, Realtek, Qualcomm, Microsoft, Dell, HP, Lenovo)
    "00:15:00" to ("Intel Corporation (Laptop)" to DeviceCategory.LAPTOP_PC),
    "00:21:6A" to ("Intel Corporation (Laptop)" to DeviceCategory.LAPTOP_PC),
    "00:24:D7" to ("Intel Corporation (Laptop)" to DeviceCategory.LAPTOP_PC),
    "28:70:4E" to ("Microsoft Surface PC" to DeviceCategory.LAPTOP_PC),
    "7C:B0:C2" to ("Microsoft Surface" to DeviceCategory.LAPTOP_PC),
    "00:0F:20" to ("HP Inc. (PC/Laptop)" to DeviceCategory.LAPTOP_PC),
    "00:14:22" to ("Dell Inc. (Laptop)" to DeviceCategory.LAPTOP_PC),
    "54:EE:75" to ("Lenovo ThinkPad" to DeviceCategory.LAPTOP_PC),
    "00:E0:4C" to ("Realtek Semiconductor" to DeviceCategory.LAPTOP_PC),
    "00:03:7F" to ("Atheros / Qualcomm PC" to DeviceCategory.LAPTOP_PC),

    // Surveillance, IP Cameras, Pinhole Cams, Covert IoT
    "00:12:12" to ("Hangzhou Xiongmai IP Cam" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "00:2A:2A" to ("Dahua Surveillance Camera" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "3C:33:00" to ("Hikvision Digital Tech" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "40:24:B2" to ("Hikvision IP Camera" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "BC:5E:CD" to ("Dahua Technology" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "10:D0:7A" to ("Tuya Smart Surveillance" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "70:C9:4E" to ("Tuya IoT Camera" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "00:0E:8F" to ("Anyka Microelectronics (Spy Cam)" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "00:6B:8E" to ("Shenzhen VStarcam" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "9C:8E:99" to ("Wyze Labs Camera" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "2C:AA:8E" to ("Wyze Labs Camera" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),
    "B0:C5:54" to ("Ring Video Surveillance" to DeviceCategory.SPY_CAMERA_SURVEILLANCE),

    // Micro-controllers, Audio Bugs & Covert Transmitters
    "24:0A:C4" to ("Espressif ESP32/ESP8266 (RF Node)" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "30:AE:A4" to ("Espressif ESP32 (Covert RF)" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "84:F3:EB" to ("Espressif IoT Module" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "A4:CF:12" to ("Espressif ESP32 Transmitter" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "B8:27:EB" to ("Raspberry Pi Recon Node" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "DC:A6:32" to ("Raspberry Pi Recon Node" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "E4:5F:01" to ("Raspberry Pi Foundation" to DeviceCategory.AUDIO_BUG_TRANSMITTER),
    "00:07:80" to ("Bluegiga Bluetooth Transmitter" to DeviceCategory.AUDIO_BUG_TRANSMITTER),

    // Aerial Drones & Gimbal Recon
    "08:EA:40" to ("DJI Aerial Recon / Drone" to DeviceCategory.DRONE_AERIAL),
    "60:60:1F" to ("DJI Aerial Recon / Drone" to DeviceCategory.DRONE_AERIAL),
    "90:D8:F3" to ("Parrot Drone UAV" to DeviceCategory.DRONE_AERIAL),
    "00:26:7E" to ("Parrot SA Drone" to DeviceCategory.DRONE_AERIAL),

    // Network Routers & Access Points
    "00:1A:1E" to ("Cisco Systems AP" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "00:18:0A" to ("Cisco Meraki AP" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "00:27:22" to ("Ubiquiti Networks UniFi" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "78:8A:20" to ("Ubiquiti Networks UniFi" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "00:14:D1" to ("NETGEAR Router" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "00:1D:7E" to ("Cisco-Linksys Router" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "00:19:E0" to ("TP-Link Technologies" to DeviceCategory.NETWORK_INFRASTRUCTURE),
    "50:D4:F7" to ("TP-Link Router" to DeviceCategory.NETWORK_INFRASTRUCTURE)
  )

  fun classifyDevice(
    macOrId: String,
    name: String,
    capabilities: String = "",
    rawHex: String = "",
    isBle: Boolean = false
  ): ClassificationResult {
    val cleanMac = macOrId.uppercase(Locale.US).replace("-", ":")
    val prefix = if (cleanMac.length >= 8) cleanMac.substring(0, 8) else ""
    val nameLower = name.lowercase(Locale.US)
    val rawHexUpper = rawHex.uppercase(Locale.US)

    // 1. Check OUI Map first
    val ouiMatch = OUI_MAP[prefix]

    // 2. Check Name patterns for Surveillance Cams
    if (nameLower.contains("camera") || nameLower.contains("ipcam") || nameLower.contains("v380") ||
      nameLower.contains("tuya") || nameLower.contains("smartlife") || nameLower.contains("spy") ||
      nameLower.contains("cctv") || nameLower.contains("dahua") || nameLower.contains("hikvision") ||
      nameLower.contains("reolink") || nameLower.contains("wyze") || nameLower.contains("anyka") ||
      nameLower.contains("xiongmai") || nameLower.contains("hdmicam") || nameLower.contains("covert")
    ) {
      return ClassificationResult(
        category = DeviceCategory.SPY_CAMERA_SURVEILLANCE,
        categoryLabel = "SURVEILLANCE CAMERA",
        vendor = ouiMatch?.first ?: "Unknown Camera Manufacturer",
        confidence = 0.95f,
        threatScore = 90,
        details = "Identified via active broadcast beacon SSID/Name signature '$name'",
        isOfflineOrUnassociated = !capabilities.contains("ESS", ignoreCase = true)
      )
    }

    // 3. Check Drones & Aerial UAV
    if (nameLower.contains("dji") || nameLower.contains("mavic") || nameLower.contains("phantom") ||
      nameLower.contains("drone") || nameLower.contains("parrot") || nameLower.contains("autel") ||
      ouiMatch?.second == DeviceCategory.DRONE_AERIAL
    ) {
      return ClassificationResult(
        category = DeviceCategory.DRONE_AERIAL,
        categoryLabel = "DRONE / AERIAL UAV",
        vendor = ouiMatch?.first ?: "UAV Platform",
        confidence = 0.92f,
        threatScore = 80,
        details = "Aerial vehicle remote controller or drone telemetry beacon",
        isOfflineOrUnassociated = true
      )
    }

    // 4. Check Trackers (Apple AirTag, Samsung SmartTag, Tile)
    if (nameLower.contains("airtag") || nameLower.contains("smarttag") || nameLower.contains("tile") ||
      rawHexUpper.contains("4C0007") || rawHexUpper.contains("4C0012") || rawHexUpper.contains("FEED")
    ) {
      return ClassificationResult(
        category = DeviceCategory.TRACKER_BEACON,
        categoryLabel = "PERSONAL BLE TRACKER",
        vendor = if (rawHexUpper.contains("4C00")) "Apple FindMy" else "Bluetooth Tracker",
        confidence = 0.94f,
        threatScore = 75,
        details = "Unassociated offline BLE beacon broadcasting location telemetry",
        isOfflineOrUnassociated = true
      )
    }

    // 5. Check Audio Bugs / Covert Micro-controllers (ESP32, Raspberry Pi)
    if (nameLower.contains("esp32") || nameLower.contains("esp8266") || nameLower.contains("espressif") ||
      nameLower.contains("raspberry") || nameLower.contains("rpi") ||
      ouiMatch?.second == DeviceCategory.AUDIO_BUG_TRANSMITTER
    ) {
      return ClassificationResult(
        category = DeviceCategory.AUDIO_BUG_TRANSMITTER,
        categoryLabel = "COVERT BUG / RF NODE",
        vendor = ouiMatch?.first ?: "Espressif Systems",
        confidence = 0.88f,
        threatScore = 85,
        details = "Micro-controller node capable of acoustic surveillance or RF sniffing",
        isOfflineOrUnassociated = true
      )
    }

    // 6. Check Smartphones (iPhones, Galaxy, Pixel, OnePlus, Xiaomi)
    if (nameLower.contains("iphone") || nameLower.contains("galaxy") || nameLower.contains("pixel") ||
      nameLower.contains("redmi") || nameLower.contains("xiaomi") || nameLower.contains("oneplus") ||
      nameLower.contains("huawei") || nameLower.contains("oppo") || nameLower.contains("vivo") ||
      nameLower.contains("direct-") || rawHexUpper.contains("4C0010") || rawHexUpper.contains("7500") ||
      rawHexUpper.contains("FE2C") || (isBle && ouiMatch?.second == DeviceCategory.SMARTPHONE)
    ) {
      val vendorName = when {
        nameLower.contains("iphone") || rawHexUpper.contains("4C00") -> "Apple iPhone"
        nameLower.contains("galaxy") || rawHexUpper.contains("7500") -> "Samsung Galaxy"
        nameLower.contains("pixel") || rawHexUpper.contains("FE2C") -> "Google Pixel"
        ouiMatch != null -> ouiMatch.first
        else -> "Nearby Mobile Handset"
      }
      return ClassificationResult(
        category = DeviceCategory.SMARTPHONE,
        categoryLabel = "SMARTPHONE",
        vendor = vendorName,
        confidence = 0.90f,
        threatScore = 20,
        details = "Mobile phone detected emitting unassociated BLE/Probe signals",
        isOfflineOrUnassociated = true
      )
    }

    // 7. Check Laptops & Desktops
    if (nameLower.contains("macbook") || nameLower.contains("laptop") || nameLower.contains("desktop") ||
      nameLower.contains("thinkpad") || nameLower.contains("surface") || nameLower.contains("dell") ||
      nameLower.contains("lenovo") || nameLower.contains("airdrop") ||
      ouiMatch?.second == DeviceCategory.LAPTOP_PC
    ) {
      return ClassificationResult(
        category = DeviceCategory.LAPTOP_PC,
        categoryLabel = "LAPTOP / COMPUTER",
        vendor = ouiMatch?.first ?: "Workstation Computer",
        confidence = 0.85f,
        threatScore = 25,
        details = "Computer wireless NIC detected emitting RF frames",
        isOfflineOrUnassociated = true
      )
    }

    // 8. Smart Wearables
    if (nameLower.contains("watch") || nameLower.contains("garmin") || nameLower.contains("fitbit") ||
      nameLower.contains("band") || nameLower.contains("buds") || nameLower.contains("airpods")
    ) {
      return ClassificationResult(
        category = DeviceCategory.SMART_WEARABLE,
        categoryLabel = "SMART WEARABLE",
        vendor = ouiMatch?.first ?: "Wearable Accessory",
        confidence = 0.88f,
        threatScore = 15,
        details = "Personal wearable accessory emitting low-power Bluetooth beacon",
        isOfflineOrUnassociated = true
      )
    }

    // 9. Match OUI if already known
    if (ouiMatch != null) {
      return ClassificationResult(
        category = ouiMatch.second,
        categoryLabel = ouiMatch.second.name.replace("_", " "),
        vendor = ouiMatch.first,
        confidence = 0.80f,
        threatScore = when (ouiMatch.second) {
          DeviceCategory.SPY_CAMERA_SURVEILLANCE -> 90
          DeviceCategory.AUDIO_BUG_TRANSMITTER -> 85
          DeviceCategory.DRONE_AERIAL -> 80
          DeviceCategory.NETWORK_INFRASTRUCTURE -> 10
          else -> 20
        },
        details = "Hardware OUI identification: ${ouiMatch.first}",
        isOfflineOrUnassociated = isBle
      )
    }

    // 10. Default classification
    val isRouter = capabilities.contains("WPA", ignoreCase = true) || capabilities.contains("ESS", ignoreCase = true)
    return if (isRouter) {
      ClassificationResult(
        category = DeviceCategory.NETWORK_INFRASTRUCTURE,
        categoryLabel = "WI-FI ACCESS POINT",
        vendor = "Standard 802.11 AP",
        confidence = 0.70f,
        threatScore = 10,
        details = "Standard Wi-Fi beacon with capabilities: $capabilities",
        isOfflineOrUnassociated = false
      )
    } else {
      ClassificationResult(
        category = if (isBle) DeviceCategory.SMARTPHONE else DeviceCategory.UNKNOWN_RF_EMITTER,
        categoryLabel = if (isBle) "UNASSOCIATED EMITTER" else "RF SIGNAL SOURCE",
        vendor = if (isBle) "Bluetooth BLE Node" else "Unknown Radio Node",
        confidence = 0.50f,
        threatScore = 30,
        details = "Raw signal emitter active in local airspace",
        isOfflineOrUnassociated = true
      )
    }
  }
}
