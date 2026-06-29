package io.blueeye.core.data.verify

import io.blueeye.core.data.tracker.model.TrackedTarget
import io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy
import io.blueeye.core.scanner.model.BleScanResultData
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Manual verification tool to analyze MAC rotation detection effectiveness on a database dump.
 * Run this via the 'verification_scripts/run_simulation.sh' script.
 */
class CorrelationAnalysisTest {

    @Test
    fun `run simulation on dumped database`() {
        // Path is relative to the project root when running from Gradle, or absolute.
        // The script generates this file in the project root.
        val csvFile = File("/Users/michal/Desktop/tracker/correlation_dataset.csv")
        
        if (!csvFile.exists()) {
            println("Skipping simulation: correlation_dataset.csv not found at ${csvFile.absolutePath}")
            return
        }

        val strategy = DeviceCorrelationStrategy()
        val targets = mutableListOf<TrackedTarget>()
        var mergedCount = 0
        var processedCount = 0

        val lines = csvFile.readLines().drop(1) // Skip header
        println("Processing ${lines.size} records...")

        lines.forEach { line ->
            val parts = line.split(",") 
            if (parts.size < 6) return@forEach

            val mac = parts[0]
            val name = parts[1].takeIf { it.isNotBlank() }
            val rawDataHex = parts[2]
            val uuidsRaw = parts[3]
            val rssi = parts[4].toIntOrNull() ?: -70
            val timestamp = parts[5].toLongOrNull() ?: System.currentTimeMillis()

            val rawData = if (rawDataHex.isNotBlank()) hexStringToByteArray(rawDataHex) else null
            val serviceUuids = if (uuidsRaw.isNotBlank()) uuidsRaw.split(";").filter { it.isNotBlank() } else emptyList()

            // Construct Scan Data
            val scanResult = BleScanResultData(
                mac = mac,
                rssi = rssi,
                timestamp = timestamp,
                technology = "BLE",
                name = name,
                manufacturerId = 0,
                manufacturerData = null,
                serviceUuids = serviceUuids,
                appearance = 0,
                txPower = 0,
                isConnectable = false,
                primaryPhy = 0,
                secondaryPhy = 0,
                rawData = rawData
            )

            val payloadHash = rawData?.contentHashCode() ?: 0

            // We use a simplified version of the logic that ignores time windows 
            // to find ALL structural matches in the history.
            val matchId = forceFindMatchStructurally(strategy, scanResult, payloadHash, name, targets)
            
            if (matchId != null) {
                // Merge
                mergedCount++
                val target = targets.first { it.targetId == matchId }
                target.macAliases.add(mac)
                target.lastSeenAt = timestamp 
                target.lastRssi = rssi
                if (rawData != null) target.lastPayload = rawData
                if (name != null) target.lastDeviceName = name
            } else {
                // Create new
                val newTarget = TrackedTarget(
                    targetId = UUID.randomUUID().toString(),
                    primaryMac = mac,
                    macAliases = mutableSetOf(mac),
                    lastPayloadHash = payloadHash,
                    lastPayload = rawData,
                    lastServiceUuids = serviceUuids.toSet(),
                    lastDeviceName = name,
                    lastRssi = rssi,
                    lastSeenAt = timestamp
                )
                targets.add(newTarget)
            }
            processedCount++
        }

        println("=== SIMULATION RESULTS ===")
        println("Processed Records: $processedCount")
        println("Unique Targets Created: ${targets.size}")
        println("Merges Detected (MAC Rotations): $mergedCount")
        println("Reduction Ratio: ${"%.2f".format(100.0 * mergedCount / processedCount)}%")
        
        targets.sortByDescending { it.macAliases.size }
        targets.take(10).forEach { t ->
            if (t.macAliases.size > 1) {
                println("Target \"${t.lastDeviceName ?: "Unknown"}\" [${t.primaryMac}] -> ${t.macAliases.size} combined MACs")
            }
        }
    }

    private fun forceFindMatchStructurally(
        strategy: DeviceCorrelationStrategy,
        data: BleScanResultData,
        payloadHash: Int,
        deviceName: String?,
        targets: Collection<TrackedTarget>
    ): String? {
        val serviceUuids = data.serviceUuids.toSet()
        val rawData = data.rawData ?: data.manufacturerData
        
         var bestMatchId: String? = null
        var highestScore = 0f

        for (target in targets) {
            // IGNORE TIME CHECK and RSSI for offline structural analysis
            
            val scoreName = matchName(deviceName, target.lastDeviceName)
            val scoreUuid = matchUuids(serviceUuids, target.lastServiceUuids)
            val scorePayload = matchPayloadFuzzy(rawData, target.lastPayload)

            val totalScore = (scoreName * 0.3f) +
                (scoreUuid * 0.3f) +
                (scorePayload * 0.4f)

            if (totalScore > 0.75f && totalScore > highestScore) {
                highestScore = totalScore
                bestMatchId = target.targetId
            }
        }
        return bestMatchId
    }
    
    // Copy of private helpers (since we can't access private members of Strategy easily from test without reflection)
    private fun matchName(nameA: String?, nameB: String?): Float {
        if (nameA.isNullOrBlank() || nameB.isNullOrBlank()) return 0f
        return if (nameA == nameB) 1.0f else 0f
    }

    private fun matchUuids(setA: Set<String>, setB: Set<String>): Float {
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        val intersection = setA.intersect(setB).size
        val union = (setA + setB).size
        return if (union > 0) intersection.toFloat() / union else 0f
    }

    private fun matchPayloadFuzzy(payloadA: ByteArray?, payloadB: ByteArray?): Float {
        if (payloadA == null || payloadB == null) return 0f
        if (payloadA.contentEquals(payloadB)) return 1.0f

        val lenA = payloadA.size
        val lenB = payloadB.size
        if (Math.abs(lenA - lenB) > Math.max(lenA, lenB) * 0.2) return 0f

        val checkLen = Math.min(lenA, lenB)
        var matches = 0
        for (i in 0 until checkLen) {
            if (payloadA[i] == payloadB[i]) {
                matches++
            }
        }
        return matches.toFloat() / checkLen
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
