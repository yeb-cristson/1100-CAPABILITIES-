package com.example.core.evidence

import android.content.Context
import com.example.core.database.AppDatabase
import com.example.core.database.EvidenceEntity
import com.example.core.model.FusionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvidenceManager(
  private val context: Context,
  private val database: AppDatabase
) {
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
  private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

  fun getAllEvidence(): Flow<List<EvidenceEntity>> {
    return database.evidenceDao().getAllEvidence()
  }

  suspend fun captureSnapshot(
    state: FusionState,
    title: String,
    notes: String = ""
  ): EvidenceEntity = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val formattedDate = dateFormat.format(Date(now))

    val telemetry = buildString {
      appendLine("=== 1100 CAPABILITIES RECON EVIDENCE LOG ===")
      appendLine("TIMESTAMP: $formattedDate (Epoch: $now)")
      appendLine("THREAT STATUS: ${state.threatLevel.name}")
      appendLine("RF DEVICES IN RANGE: ${state.rfDeviceCount}")
      appendLine("BLE TRACKERS IDENTIFIED: ${state.bleTrackerCount}")
      appendLine("SUBNET HOSTS: ${state.subnetHostCount}")
      appendLine("RTSP VIDEO STREAMS: ${state.rtspCameraCount}")
      appendLine("EM FLUX: ${state.emMagnitudeUt} uT (SPIKE: ${state.emSpikeActive})")
      appendLine("IR CAMERA GLINTS: ${state.glintCount}")
      appendLine("ULTRASONIC ACOUSTIC EMISSION: ${state.ultrasonicActive} (${state.acousticPeakHz} Hz)")
      if (notes.isNotBlank()) {
        appendLine("OPERATOR NOTES: $notes")
      }
      appendLine("ACTIVE ALERTS:")
      if (state.activeAlerts.isEmpty()) {
        appendLine("  [None - Environment Nominal]")
      } else {
        state.activeAlerts.forEach { alert ->
          appendLine("  - [${alert.level}] ${alert.title}: ${alert.details} (Source: ${alert.sourceModule})")
        }
      }
    }

    val sha256 = computeSha256(telemetry)

    val entity = EvidenceEntity(
      timestamp = now,
      formattedDate = formattedDate,
      sha256Digest = sha256,
      title = title.ifBlank { "Recon Snapshot - $formattedDate" },
      threatLevel = state.threatLevel.name,
      summary = "RF: ${state.rfDeviceCount} | Cam: ${state.rtspCameraCount} | EM: %.1fuT | Glint: ${state.glintCount}".format(state.emMagnitudeUt),
      rawTelemetryJson = telemetry
    )

    val id = database.evidenceDao().insertEvidence(entity)

    // Also write signed text file to local app storage
    try {
      val fileName = "EVIDENCE_${fileDateFormat.format(Date(now))}.txt"
      val file = File(context.filesDir, fileName)
      file.writeText("$telemetry\n\n=== CRYPTOGRAPHIC SHA-256 SIGNATURE ===\n$sha256\n")
    } catch (_: Exception) {}

    entity.copy(id = id)
  }

  suspend fun deleteEvidence(id: Long) = withContext(Dispatchers.IO) {
    database.evidenceDao().deleteEvidence(id)
  }

  suspend fun clearAll() = withContext(Dispatchers.IO) {
    database.evidenceDao().clearAll()
  }

  private fun computeSha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
  }
}
