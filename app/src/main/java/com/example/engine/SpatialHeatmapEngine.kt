package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.example.core.database.ReconRepository
import com.example.core.model.*
import com.example.core.util.DeviceCategory
import com.example.core.util.SurveillanceClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

class SpatialHeatmapEngine(
  private val context: Context,
  private val repository: ReconRepository
) : LocationListener, SensorEventListener {

  private val scope = CoroutineScope(Dispatchers.Default)

  private val locationManager by lazy {
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  }
  private val sensorManager by lazy {
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  }

  private val _heatmapState = MutableStateFlow(SpatialHeatmapState())
  val heatmapState: StateFlow<SpatialHeatmapState> = _heatmapState.asStateFlow()

  private var currentLat = 37.7749
  private var currentLon = -122.4194
  private var currentAlt = 15.0
  private var currentAccuracy = 4.2f
  private var currentHeadingDeg = 0.0f

  private val gravity = FloatArray(3)
  private val geomagnetic = FloatArray(3)
  private var hasGravity = false
  private var hasGeomagnetic = false

  private val trackedDevices = mutableMapOf<String, SpatialDevicePoint>()

  @SuppressLint("MissingPermission")
  fun start() {
    // 1. Request GPS & Network Location
    try {
      locationManager?.let { lm ->
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
          lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, this)
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
          lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 1.0f, this)
        }
        val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
          ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        lastGps?.let { onLocationChanged(it) }
      }
    } catch (_: Exception) {}

    // 2. Register Orientation / Rotation Sensors
    try {
      sensorManager?.let { sm ->
        val rotationVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVec != null) {
          sm.registerListener(this, rotationVec, SensorManager.SENSOR_DELAY_UI)
        } else {
          val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
          val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
          if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
          if (mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        }
      }
    } catch (_: Exception) {}
  }

  fun stop() {
    try {
      locationManager?.removeUpdates(this)
      sensorManager?.unregisterListener(this)
    } catch (_: Exception) {}
  }

  override fun onLocationChanged(location: Location) {
    currentLat = location.latitude
    currentLon = location.longitude
    currentAlt = location.altitude
    currentAccuracy = location.accuracy

    updateSpatialMatrix()
  }

  override fun onSensorChanged(event: SensorEvent?) {
    event ?: return
    when (event.sensor.type) {
      Sensor.TYPE_ROTATION_VECTOR -> {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthRad = orientation[0]
        currentHeadingDeg = ((Math.toDegrees(azimuthRad.toDouble()) + 360) % 360).toFloat()
        updateSpatialMatrix()
      }
      Sensor.TYPE_ACCELEROMETER -> {
        System.arraycopy(event.values, 0, gravity, 0, 3)
        hasGravity = true
        calculateOrientationIfReady()
      }
      Sensor.TYPE_MAGNETIC_FIELD -> {
        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
        hasGeomagnetic = true
        calculateOrientationIfReady()
      }
    }
  }

  private fun calculateOrientationIfReady() {
    if (hasGravity && hasGeomagnetic) {
      val r = FloatArray(9)
      val i = FloatArray(9)
      if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
        val orientation = FloatArray(3)
        SensorManager.getOrientation(r, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble())
        currentHeadingDeg = ((azimuth + 360) % 360).toFloat()
        updateSpatialMatrix()
      }
    }
  }

  /**
   * Ingests RF devices and maps them onto the 2D Spatial coordinate grid
   */
  fun ingestRfDevices(devices: List<RfDevice>) {
    scope.launch {
      devices.forEach { rf ->
        val classified = SurveillanceClassifier.classifyDevice(
          macOrId = rf.id,
          name = rf.name,
          capabilities = rf.capabilities,
          rawHex = rf.rawData,
          isBle = rf.type == RfType.BLE
        )

        val hashBearing = ((rf.id.hashCode().absoluteValue % 360)).toFloat()
        val dist = rf.distanceMeters.coerceIn(0.5, 35.0)

        // Convert polar (distance, angle) to Cartesian relative (meters)
        val rad = Math.toRadians(hashBearing.toDouble())
        val relX = (dist * sin(rad)).toFloat()
        val relY = (dist * cos(rad)).toFloat()

        // Approximate geographic offset (1 deg lat ~= 111,320m, 1 deg lon ~= 111,320m * cos(lat))
        val latOffset = relY / 111320.0
        val lonOffset = relX / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.0001))

        val densityWeight = ((rf.rssi + 100).coerceIn(10, 100) / 100f) *
          if (classified.category == DeviceCategory.SPY_CAMERA_SURVEILLANCE) 1.5f else 1.0f

        val spatialPoint = SpatialDevicePoint(
          id = rf.id,
          name = if (rf.name.isNotBlank() && !rf.name.startsWith("<")) rf.name else classified.categoryLabel,
          identifier = rf.id,
          category = classified.category,
          categoryLabel = classified.categoryLabel,
          vendor = classified.vendor,
          rssi = rf.rssi,
          distanceMeters = dist,
          bearingDeg = hashBearing,
          relX = relX,
          relY = relY,
          latitude = currentLat + latOffset,
          longitude = currentLon + lonOffset,
          densityWeight = densityWeight,
          threatScore = classified.threatScore,
          isOfflineOrUnassociated = classified.isOfflineOrUnassociated,
          lastSeenMs = System.currentTimeMillis()
        )

        trackedDevices[rf.id] = spatialPoint
      }
      updateSpatialMatrix()
    }
  }

  /**
   * Ingests Subnet hosts onto spatial map
   */
  fun ingestSubnetHosts(hosts: List<SubnetHost>) {
    scope.launch {
      hosts.forEach { h ->
        val classified = SurveillanceClassifier.classifyDevice(
          macOrId = h.macAddress.ifBlank { h.ip },
          name = h.hostName.ifBlank { h.banner },
          capabilities = if (h.isRtspCamera) "RTSP_CAM" else "LAN_HOST",
          rawHex = "",
          isBle = false
        )

        val hashBearing = ((h.ip.hashCode().absoluteValue % 360)).toFloat()
        val dist = if (h.isRtspCamera) 3.5 else 6.0

        val rad = Math.toRadians(hashBearing.toDouble())
        val relX = (dist * sin(rad)).toFloat()
        val relY = (dist * cos(rad)).toFloat()

        val latOffset = relY / 111320.0
        val lonOffset = relX / (111320.0 * cos(Math.toRadians(currentLat)).coerceAtLeast(0.0001))

        val point = SpatialDevicePoint(
          id = "ip-${h.ip}",
          name = if (h.hostName.isNotBlank()) h.hostName else if (h.isRtspCamera) "IP Camera (${h.ip})" else "Host ${h.ip}",
          identifier = "${h.ip} / ${h.macAddress}",
          category = if (h.isRtspCamera) DeviceCategory.SPY_CAMERA_SURVEILLANCE else classified.category,
          categoryLabel = if (h.isRtspCamera) "SURVEILLANCE CAMERA" else classified.categoryLabel,
          vendor = classified.vendor,
          rssi = -55,
          distanceMeters = dist,
          bearingDeg = hashBearing,
          relX = relX,
          relY = relY,
          latitude = currentLat + latOffset,
          longitude = currentLon + lonOffset,
          densityWeight = 0.85f,
          threatScore = if (h.isRtspCamera) 95 else classified.threatScore,
          isOfflineOrUnassociated = false,
          lastSeenMs = System.currentTimeMillis()
        )
        trackedDevices[point.id] = point
      }
      updateSpatialMatrix()
    }
  }

  fun setFilterCategory(cat: DeviceCategory?) {
    _heatmapState.value = _heatmapState.value.copy(selectedFilterCategory = cat)
  }

  private fun updateSpatialMatrix() {
    val list = trackedDevices.values.toList()
    val filterCat = _heatmapState.value.selectedFilterCategory
    val filteredList = if (filterCat != null) list.filter { it.category == filterCat } else list

    val totalDensity = if (filteredList.isNotEmpty()) {
      filteredList.sumOf { it.densityWeight.toDouble() }.toFloat() / (PI * 30.0 * 30.0).toFloat() * 100f
    } else 0f

    val peakCluster = filteredList.count { it.distanceMeters <= 5.0 }

    _heatmapState.value = _heatmapState.value.copy(
      userLocation = UserSpatialCoordinate(
        latitude = currentLat,
        longitude = currentLon,
        altitudeM = currentAlt,
        accuracyM = currentAccuracy,
        headingDegrees = currentHeadingDeg,
        provider = "GPS_FUSION",
        timestamp = System.currentTimeMillis()
      ),
      spatialDevices = filteredList.sortedBy { it.distanceMeters },
      totalDeviceDensity = totalDensity,
      peakClusterCount = peakCluster,
      coverageRadiusMeters = 30f
    )
  }

  /**
   * Generates formatted GeoJSON string for D3.js and GIS export
   */
  fun exportGeoJson(): String {
    val root = JSONObject()
    root.put("type", "FeatureCollection")

    val features = JSONArray()

    // 1. User Location Feature
    val userFeat = JSONObject()
    userFeat.put("type", "Feature")
    userFeat.put("properties", JSONObject().apply {
      put("name", "Current Recon Position")
      put("type", "USER_OPERATOR")
      put("accuracy_m", currentAccuracy)
      put("heading_deg", currentHeadingDeg)
    })
    userFeat.put("geometry", JSONObject().apply {
      put("type", "Point")
      put("coordinates", JSONArray().apply {
        put(currentLon)
        put(currentLat)
      })
    })
    features.put(userFeat)

    // 2. Spatial Devices
    _heatmapState.value.spatialDevices.forEach { dev ->
      val devFeat = JSONObject()
      devFeat.put("type", "Feature")
      devFeat.put("properties", JSONObject().apply {
        put("id", dev.id)
        put("name", dev.name)
        put("category", dev.category.name)
        put("category_label", dev.categoryLabel)
        put("vendor", dev.vendor)
        put("rssi", dev.rssi)
        put("distance_m", dev.distanceMeters)
        put("rel_x", dev.relX)
        put("rel_y", dev.relY)
        put("threat_score", dev.threatScore)
        put("density_weight", dev.densityWeight)
        put("is_offline_unassociated", dev.isOfflineOrUnassociated)
      })
      devFeat.put("geometry", JSONObject().apply {
        put("type", "Point")
        put("coordinates", JSONArray().apply {
          put(dev.longitude)
          put(dev.latitude)
        })
      })
      features.put(devFeat)
    }

    root.put("features", features)
    return root.toString(2)
  }

  /**
   * Builds the self-contained D3.js HTML5 Interactive Spatial Heatmap Payload
   */
  fun generateD3HeatmapHtml(): String {
    val state = _heatmapState.value
    val devicesJson = JSONArray().apply {
      state.spatialDevices.forEach { dev ->
        put(JSONObject().apply {
          put("id", dev.id)
          put("name", dev.name)
          put("category", dev.category.name)
          put("categoryLabel", dev.categoryLabel)
          put("vendor", dev.vendor)
          put("rssi", dev.rssi)
          put("distance", dev.distanceMeters)
          put("bearing", dev.bearingDeg)
          put("x", dev.relX)
          put("y", dev.relY)
          put("weight", dev.densityWeight)
          put("threat", dev.threatScore)
          put("isOffline", dev.isOfflineOrUnassociated)
        })
      }
    }.toString()

    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <title>Spatial RF Density Heatmap</title>
        <script src="https://d3js.org/d3.v7.min.js"></script>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body {
            background-color: #050505;
            color: #E4E4E7;
            font-family: 'Courier New', Courier, monospace;
            overflow: hidden;
            width: 100vw;
            height: 100vh;
            user-select: none;
          }
          #canvas-container {
            width: 100%;
            height: 100%;
            position: relative;
          }
          svg {
            width: 100%;
            height: 100%;
            display: block;
          }
          .grid-ring {
            fill: none;
            stroke: #1A2634;
            stroke-width: 1;
            stroke-dasharray: 4,4;
          }
          .grid-axis {
            stroke: #15222E;
            stroke-width: 1;
          }
          .ring-label {
            fill: #4B6375;
            font-size: 9px;
            text-anchor: middle;
          }
          .user-pulse {
            fill: #00E5FF;
            fill-opacity: 0.2;
            animation: pulse 2s infinite ease-out;
          }
          .user-center {
            fill: #00E5FF;
            stroke: #FFFFFF;
            stroke-width: 2;
          }
          .heading-cone {
            fill: url(#heading-grad);
            opacity: 0.35;
          }
          .device-node {
            cursor: pointer;
            transition: transform 0.2s;
          }
          .device-node:hover {
            transform: scale(1.4);
          }
          .tooltip {
            position: absolute;
            bottom: 12px;
            left: 12px;
            right: 12px;
            background: rgba(10, 14, 20, 0.95);
            border: 1px solid #00E5FF;
            border-radius: 6px;
            padding: 8px 12px;
            font-size: 11px;
            color: #FFFFFF;
            pointer-events: none;
            display: none;
            box-shadow: 0 4px 20px rgba(0, 229, 255, 0.25);
          }
          .tooltip-title {
            color: #00E5FF;
            font-weight: bold;
            margin-bottom: 2px;
          }
          .hud-badge {
            position: absolute;
            top: 10px;
            left: 10px;
            background: rgba(10, 14, 20, 0.85);
            border: 1px solid #27272A;
            border-radius: 4px;
            padding: 4px 8px;
            font-size: 10px;
            color: #00E5FF;
          }
          @keyframes pulse {
            0% { r: 6px; opacity: 0.8; }
            100% { r: 35px; opacity: 0; }
          }
        </style>
      </head>
      <body>
        <div id="canvas-container">
          <div class="hud-badge" id="density-hud">SPATIAL D3 ENGINE ACTIVE | RANGE: 30m</div>
          <svg id="heatmap-svg"></svg>
          <div class="tooltip" id="tooltip"></div>
        </div>

        <script>
          const rawDevices = $devicesJson;
          const userHeading = ${state.userLocation.headingDegrees};
          const userLat = ${state.userLocation.latitude};
          const userLon = ${state.userLocation.longitude};
          const userAccuracy = ${state.userLocation.accuracyM};

          const width = window.innerWidth;
          const height = window.innerHeight;
          const maxRadius = Math.min(width, height) * 0.44;
          const maxRangeMeters = 30;
          const scaleFactor = maxRadius / maxRangeMeters;

          const svg = d3.select("#heatmap-svg")
            .attr("viewBox", [0, 0, width, height]);

          const defs = svg.append("defs");

          // Heading cone gradient
          const headingGrad = defs.append("linearGradient")
            .attr("id", "heading-grad")
            .attr("x1", "0%").attr("y1", "100%")
            .attr("x2", "0%").attr("y2", "0%");
          headingGrad.append("stop").attr("offset", "0%").attr("stop-color", "#00E5FF").attr("stop-opacity", 0.6);
          headingGrad.append("stop").attr("offset", "100%").attr("stop-color", "#00E5FF").attr("stop-opacity", 0);

          // Heat gradient definitions
          const colorScale = d3.scaleSequential()
            .domain([0, 1])
            .interpolator(d3.interpolateRgbBasis(["rgba(0,229,255,0.05)", "rgba(0,230,118,0.25)", "rgba(255,234,0,0.5)", "rgba(255,42,60,0.85)"]));

          const g = svg.append("g")
            .attr("transform", "translate(" + (width/2) + ", " + (height/2) + ")");

          // 1. Radar Polar Grid Rings
          const rings = [5, 10, 15, 20, 25, 30];
          rings.forEach(r => {
            const rad = r * scaleFactor;
            g.append("circle")
              .attr("class", "grid-ring")
              .attr("r", rad);

            g.append("text")
              .attr("class", "ring-label")
              .attr("y", -rad + 10)
              .text(r + "m");
          });

          // Axes
          g.append("line").attr("class", "grid-axis").attr("x1", -maxRadius).attr("y1", 0).attr("x2", maxRadius).attr("y2", 0);
          g.append("line").attr("class", "grid-axis").attr("x1", 0).attr("y1", -maxRadius).attr("x2", 0).attr("y2", maxRadius);

          // 2. Continuous Gaussian Density Field (Canvas/SVG Heatmap Blobs)
          const heatGroup = g.append("g").attr("class", "heat-layer");
          rawDevices.forEach(d => {
            const px = d.x * scaleFactor;
            const py = -d.y * scaleFactor;
            const rBlob = Math.max(25, (40 - d.distance) * 1.6);

            const radialGradId = "heat-blob-" + Math.abs(d.id ? d.id.replace(/[^a-zA-Z0-9]/g, "") : Math.floor(Math.random()*100000));
            const radGrad = defs.append("radialGradient").attr("id", radialGradId);
            
            const peakColor = d.threat >= 75 ? "rgba(255,42,60,0.7)" : (d.threat >= 40 ? "rgba(255,234,0,0.6)" : "rgba(0,229,255,0.5)");
            radGrad.append("stop").attr("offset", "0%").attr("stop-color", peakColor);
            radGrad.append("stop").attr("offset", "50%").attr("stop-color", "rgba(0,230,118,0.2)");
            radGrad.append("stop").attr("offset", "100%").attr("stop-color", "rgba(0,0,0,0)");

            heatGroup.append("circle")
              .attr("cx", px)
              .attr("cy", py)
              .attr("r", rBlob)
              .attr("fill", "url(#" + radialGradId + ")")
              .style("mix-blend-mode", "screen");
          });

          // 3. User Heading Cone
          const coneAngle = 40;
          const coneLen = maxRadius * 0.9;
          const leftRad = (userHeading - 90 - coneAngle/2) * Math.PI / 180;
          const rightRad = (userHeading - 90 + coneAngle/2) * Math.PI / 180;

          const conePath = "M 0 0 L " + (coneLen * Math.cos(leftRad)) + " " + (coneLen * Math.sin(leftRad)) + " A " + coneLen + " " + coneLen + " 0 0 1 " + (coneLen * Math.cos(rightRad)) + " " + (coneLen * Math.sin(rightRad)) + " Z";
          g.append("path")
            .attr("d", conePath)
            .attr("class", "heading-cone");

          // 4. User Center Position
          g.append("circle").attr("class", "user-pulse").attr("r", 10);
          g.append("circle").attr("class", "user-center").attr("r", 5);

          // 5. Device Node Glyphs
          const nodesGroup = g.append("g").attr("class", "nodes-layer");
          const tooltip = document.getElementById("tooltip");

          rawDevices.forEach(d => {
            const px = d.x * scaleFactor;
            const py = -d.y * scaleFactor;

            const node = nodesGroup.append("g")
              .attr("class", "device-node")
              .attr("transform", "translate(" + px + ", " + py + ")");

            const nodeColor = d.threat >= 75 ? "#FF2A3C" : (d.threat >= 40 ? "#FFEA00" : (d.isOffline ? "#00E5FF" : "#00E676"));

            node.append("circle")
              .attr("r", 6)
              .attr("fill", nodeColor)
              .attr("stroke", "#050505")
              .attr("stroke-width", 1.5);

            node.append("circle")
              .attr("r", 10)
              .attr("fill", "none")
              .attr("stroke", nodeColor)
              .attr("stroke-width", 0.75)
              .attr("stroke-dasharray", "2,2");

            node.on("click", function(e) {
              e.stopPropagation();
              tooltip.style.display = "block";
              tooltip.innerHTML = "<div class='tooltip-title'>[" + d.categoryLabel + "] " + d.name + "</div>" +
                "<div>ID: " + d.id + " | RSSI: " + d.rssi + " dBm | Dist: " + d.distance.toFixed(1) + "m</div>" +
                "<div>Vendor: " + d.vendor + " | Threat Score: " + d.threat + "/100</div>" +
                "<div style='color:" + (d.isOffline ? "#00E5FF" : "#A1A1AA") + "'>Mode: " + (d.isOffline ? "OFFLINE / UNASSOCIATED SNIFFER" : "SUBNET / AP ASSOCIATED") + "</div>";
              if (window.AndroidBridge && window.AndroidBridge.onDeviceSelected) {
                window.AndroidBridge.onDeviceSelected(d.id);
              }
            });
          });

          svg.on("click", () => {
            tooltip.style.display = "none";
          });

          // Zoom & Pan support
          const zoom = d3.zoom()
            .scaleExtent([0.6, 4])
            .on("zoom", (event) => {
              g.attr("transform", event.transform);
            });
          svg.call(zoom);
          svg.call(zoom.transform, d3.zoomIdentity.translate(width/2, height/2));
        </script>
      </body>
      </html>
    """.trimIndent()
  }

  override fun onProviderEnabled(provider: String) {}
  override fun onProviderDisabled(provider: String) {}
  override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
