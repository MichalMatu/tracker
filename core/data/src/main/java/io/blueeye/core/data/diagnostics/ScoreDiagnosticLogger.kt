package io.blueeye.core.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.tracker.FollowMeScoreCalculator
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs detailed score calculation results to a CSV file for post-analysis.
 * This helps debug WHY a device got a certain score in the field.
 *
 * File location: /Android/data/io.blueeye/files/tracker_diagnostics.csv
 */
@Singleton
class ScoreDiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val file by lazy {
        File(context.getExternalFilesDir(null), "tracker_diagnostics.csv")
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // Limit log size to ~10MB to avoid eating storage
    private val MAX_FILE_SIZE = 10 * 1024 * 1024L 

    // Cache to debounce logs: MAC -> LastLogState
    private val logCache = java.util.concurrent.ConcurrentHashMap<String, LogState>()

    private data class LogState(
        val score: Int,
        val status: TrackingStatus,
        val timestamp: Long
    )

    private val HEARTBEAT_MS = 30_000L // Log at least every 30s if active

    suspend fun logScore(
        mac: String,
        result: FollowMeScoreCalculator.ScoreResult,
        latitude: Double?,
        longitude: Double?
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Throttling Logic
                val now = System.currentTimeMillis()
                val lastState = logCache[mac]
                
                val shouldLog = when {
                    lastState == null -> true // First time seeing device
                    lastState.score != result.totalScore -> true // Score changed
                    lastState.status != result.status -> true // Status changed
                    (now - lastState.timestamp) > HEARTBEAT_MS -> true // Heartbeat
                    else -> false
                }

                if (!shouldLog) return@withContext

                // Update cache
                logCache[mac] = LogState(result.totalScore, result.status, now)

                ensureHeader()
                
                // Rotation check
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                     // Simple rotation: delete and start over for field test simplicity
                     file.delete()
                     ensureHeader()
                     logCache.clear() // Clear cache on rotation
                }

                val timestampStr = dateFormat.format(Date(now))
                // CSV Format: Time,MAC,Score,Status,Dur,Rssi,Type,Mac,Enc,Lat,Lon,Expl
                val line = buildString {
                    append(timestampStr).append(",")
                    append(mac).append(",")
                    append(result.totalScore).append(",")
                    append(result.status.name).append(",")
                    append(result.durationScore).append(",")
                    append(result.rssiStabilityScore).append(",")
                    append(result.deviceTypeScore).append(",")
                    append(result.macBehaviorScore).append(",")
                    append(result.encounterScore).append(",")
                    append(latitude ?: "").append(",")
                    append(longitude ?: "").append(",")
                    append("\"").append(result.explanation.replace("\"", "'")).append("\"\n")
                }
                
                FileWriter(file, true).use {
                    it.write(line)
                }

            } catch (e: Exception) {
                // Ignore logging errors to not crash app
                e.printStackTrace()
            }
        }
    }

    private fun ensureHeader() {
        if (!file.exists()) {
            file.createNewFile()
            FileWriter(file, true).use {
                it.write("Time,MAC,TotalScore,Status,DurScore,RssiScore,TypeScore,MacScore,EncScore,Lat,Lon,Explanation\n")
            }
        }
    }
}
