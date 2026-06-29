package io.blueeye.core.data.tracker

import android.util.Log
import io.blueeye.core.data.tracker.model.CarryoverMatch
import io.blueeye.core.data.tracker.model.CorrelationResult
import io.blueeye.core.data.tracker.model.RecentObservation
import io.blueeye.core.data.tracker.model.TrackedTarget
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.scanner.model.BleScanResultData
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Address Carryover Tracker - Correlates devices across MAC address changes.
 *
 * Problem: BLE devices with RPA (Resolvable Private Address) change their MAC address every ~15
 * minutes. This makes tracking difficult.
 *
 * Solution: Use payload fingerprinting to correlate different MAC addresses that belong to the same
 * physical device.
 *
 * Heuristics:
 * 1. Manufacturer Data payload changes slower than MAC address
 * 2. Service UUIDs are usually constant
 * 3. RSSI should be similar if device hasn't moved
 * 4. Device name (if present) is usually constant
 */
@Singleton
class AddressCarryoverTracker
@Inject
constructor(
    private val correlationStrategy: io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy,
) {
    // Active targets being tracked
    private val targets = ConcurrentHashMap<String, TrackedTarget>()

    // Recent observations for correlation (sliding window)
    private val recentObservations = ConcurrentHashMap<String, RecentObservation>()

    // MAC to Target mapping
    private val macToTarget = ConcurrentHashMap<String, String>()

    // Pending observations (weak signals that shouldn't be persisted yet)
    private val pendingObservations = ConcurrentHashMap<String, Long>() // Mac -> Timestamp
    
    // Flag to prevent double initialization
    @Volatile
    private var isRehydrated = false

    /**
     * Rehydrate tracker state from database on app startup.
     * This restores MAC → fingerprint mappings for devices that were tracked before restart.
     * Should be called once during app initialization.
     */
    fun rehydrateFromDatabase(devices: List<io.blueeye.core.data.db.entity.DeviceEntity>) {
        if (isRehydrated) {
            Log.w(TAG, "Already rehydrated, skipping.")
            return
        }
        
        synchronized(this) {
            var restored = 0
            for (device in devices) {
                val fingerprint = device.fingerprint
                val mac = device.lastMacAddress ?: continue // Skip if MAC is null
                
                // Skip if fingerprint == mac (no carryover case)
                if (fingerprint == mac) continue
                
                // Create a minimal TrackedTarget for correlation purposes
                val target = TrackedTarget(
                    targetId = fingerprint, // Use fingerprint as stable ID
                    primaryMac = fingerprint,
                    macAliases = mutableSetOf(fingerprint, mac),
                    lastPayloadHash = device.lastRawData?.hashCode() ?: 0,
                    lastPayload = null,
                    lastServiceUuids = device.gattServices?.split(",")?.toSet() ?: emptySet(),
                    lastDeviceName = device.lastDeviceName,
                    lastRssi = device.lastRssi,
                    lastSeenAt = device.lastSeenAt,
                    lastAdvertisingInterval = device.advertisingIntervalMs
                )
                
                targets[fingerprint] = target
                macToTarget[fingerprint] = fingerprint
                macToTarget[mac] = fingerprint
                restored++
            }
            
            isRehydrated = true
            Log.i(TAG, "🔄 Rehydrated $restored devices from database (total devices: ${devices.size})")
        }
    }

    /**
     * Process a new scan result and attempt to correlate with existing targets.
     *
     * @return Target ID (stable identifier) or null if new device
     */
    /**
     * Process a new scan result and attempt to correlate with existing targets.
     *
     * @return Target ID (stable identifier) or null if new device
     */
    fun processScan(
        data: BleScanResultData,
        deviceName: String?,
    ): CorrelationResult = synchronized(this) {
        // Use rawData for hash if available, as it captures the entire packet uniqueness
        // (including Service Data which Bose uses instead of Manufacturer Data)
        val payloadHash = computePayloadHash(data.manufacturerData, data.rawData)
        val uuidSet = data.serviceUuids.toSet()
        val advertisingInterval = extractAdvertisingInterval(data.rawData)

        // 1. Check if we already know this MAC
        val existingTargetId = macToTarget[data.mac]
        if (existingTargetId != null) {
            val target = targets[existingTargetId]
            if (target != null) {
                target.update(data.rssi, data.timestamp, payloadHash, uuidSet, deviceName, data.rawData ?: data.manufacturerData, advertisingInterval)
                val correlatedMac = target.primaryMac.takeIf { it != data.mac }
                return CorrelationResult(
                    targetId = existingTargetId,
                    isNewTarget = false,
                    isCarryover = false,
                    isPending = false,
                    correlatedMac = correlatedMac,
                    macChangeCount = target.macChangeCount,
                )
            }
        }

        // 2. Check if this is a "Weak" signal (Random MAC, no name, no significant payload)
        // If so, we mark it as pending and DO NOT persist it yet.
        // We only persist if we have enough data to be useful or if it's a Public MAC.
        // NOTE: We only filter RANDOM addresses. Public ones are always valuable.
        // Note: We use a simple heuristic for now.
        // Actually, let's look at the data strength.
        val isStrong = isDataStrong(deviceName, uuidSet, data.manufacturerData, data.rssi)
        
        if (!isStrong) {
            // It's a weak signal. Use pending state.
            // But if we already have it in pending, update timestamp?
            pendingObservations[data.mac] = data.timestamp
            return CorrelationResult(
                targetId = "", // No target ID yet
                isNewTarget = false, 
                isCarryover = false, 
                isPending = true, 
                correlatedMac = null,
            )
        } else {
            // It IS strong. If it was pending, we can remove it (promotion).
            pendingObservations.remove(data.mac)
        }

        // 2. Try to find a carryover match (MAC changed but same device)
        val carryoverMatch = correlationStrategy.findMatch(
            data,
            deviceName,
            advertisingInterval,
            targets.values
        )

        val result = if (carryoverMatch != null) {
            handleCarryover(
                carryoverMatch,
                data,
                payloadHash,
                advertisingInterval,
                deviceName,
            )
        } else {
            handleNewTarget(
                data.mac,
                data.rssi,
                data.timestamp,
                payloadHash,
                uuidSet,
                deviceName,
                data.rawData ?: data.manufacturerData,
                advertisingInterval
            )
        }
        
        return result
    }

    private fun handleCarryover(
        carryoverMatch: CarryoverMatch,
        data: BleScanResultData,
        payloadHash: Int,
        interval: Long?,
        deviceName: String?,
    ): CorrelationResult {
        val targetId = carryoverMatch.targetId
        val target = targets[targetId]
        if (target != null) {
            if (target.macAliases.size < MAX_MAC_ALIASES) {
                target.macAliases.add(data.mac)
            }
            target.macChangeCount++
            target.lastRssi = data.rssi
            target.lastSeenAt = data.timestamp
            if (payloadHash != 0) {
                target.lastPayloadHash = payloadHash
            }
            if (interval != null) {
                target.lastAdvertisingInterval = interval
            }
            
            val currentName = target.lastDeviceName
            if (!deviceName.isNullOrBlank()) {
                val isNewGeneric = isGenericName(deviceName)
                val isCurrentSpecific = !currentName.isNullOrBlank() && !isGenericName(currentName)
                val shouldKeepCurrentName = isNewGeneric && isCurrentSpecific

                if (!shouldKeepCurrentName) {
                    target.lastDeviceName = deviceName
                }
            }

            target.riskScore += MAC_CHANGE_RISK_BUMP
            macToTarget[data.mac] = targetId

            Log.i(
                TAG,
                "MAC identity carryover ${data.mac} -> ${target.primaryMac}; changes=${target.macChangeCount}",
            )

            return CorrelationResult(
                targetId = targetId,
                isNewTarget = false,
                isCarryover = true,
                isPending = false,
                correlatedMac = target.primaryMac,
                macChangeCount = target.macChangeCount,
                matchEvidence = carryoverMatch.evidence,
            )
        }
        return CorrelationResult(
            targetId = targetId,
            isNewTarget = false,
            isCarryover = false,
            isPending = false,
            correlatedMac = null,
        ) // Should not happen
    }
    
    // Delegate to shared utility to avoid drift
    private fun isGenericName(name: String): Boolean {
        return io.blueeye.core.data.util.NameUtils.isGenericName(name)
    }

    private fun handleNewTarget(
        mac: String,
        rssi: Int,
        timestamp: Long,
        payloadHash: Int,
        uuidSet: Set<String>,
        deviceName: String?,
        payload: ByteArray?,
        interval: Long?
    ): CorrelationResult {
        val newTargetId = generateTargetId()
        val newTarget = TrackedTarget(
            targetId = newTargetId,
            primaryMac = mac,
            macAliases = mutableSetOf(mac),
            lastPayloadHash = payloadHash,
            lastPayload = payload,
            lastServiceUuids = uuidSet,
            lastDeviceName = deviceName,
            lastRssi = rssi,
            lastSeenAt = timestamp,
            lastAdvertisingInterval = interval
        )

        targets[newTargetId] = newTarget
        macToTarget[mac] = newTargetId

        recentObservations[mac] = RecentObservation(
            mac, payloadHash, uuidSet, deviceName, rssi, timestamp, payload
        )

        return CorrelationResult(
            targetId = newTargetId,
            isNewTarget = true,
            isCarryover = false,
            isPending = false,
            correlatedMac = null,
        )
    }

    private fun TrackedTarget.update(
        rssi: Int,
        timestamp: Long,
        payloadHash: Int,
        uuidSet: Set<String>,
        deviceName: String?,
        payload: ByteArray?,
        interval: Long?
    ) {
        lastRssi = rssi
        lastSeenAt = timestamp
        if (payloadHash != 0) {
            lastPayloadHash = payloadHash
        }
        if (payload != null) {
            lastPayload = payload
        }
        lastServiceUuids = uuidSet
        lastDeviceName = deviceName ?: lastDeviceName
        if (interval != null) {
            lastAdvertisingInterval = interval
        }
    }

    /** Compute hash of payload (prioritize rawData for better uniqueness). */
    private fun computePayloadHash(
        manufacturerData: ByteArray?,
        rawData: ByteArray?,
    ): Int {
        return when {
            rawData != null && rawData.isNotEmpty() -> rawData.contentHashCode()
            manufacturerData != null && manufacturerData.isNotEmpty() -> manufacturerData.contentHashCode()
            else -> 0
        }
    }

    private fun generateTargetId(): String {
        val randomNum = (Math.random() * TARGET_ID_RANDOM_MAX).toInt()
        return "TGT_${System.currentTimeMillis()}_$randomNum"
    }

    private fun isDataStrong(
        name: String?,
        uuids: Set<String>,
        manufacturerData: ByteArray?,
        rssi: Int
    ): Boolean {
        // 1. Name is present (Strongest signal of "Not Noise")
        if (!name.isNullOrBlank()) return true
        
        // 2. UUIDs are present (Strong signal of connectable device/peripheral)
        if (uuids.isNotEmpty()) return true
        
        // 3. Manufacturer Data is present
        // But if the signal is very weak (< -90 dBm), we ignore this to filter out distant noise/beacons.
        // We only accept Manuf Data as "Strong" if the signal is decent.
        if (rssi > -90 && manufacturerData != null && manufacturerData.isNotEmpty()) return true
        
        // 4. Raw Data -> Too generic, ignored.
        
        return false
    }

    companion object {
        private const val TAG = "CarryoverTracker"

        /** Maximum number of MAC aliases per target */
        private const val MAX_MAC_ALIASES = 10
        private const val MAC_CHANGE_RISK_BUMP = 10
        private const val TARGET_ID_RANDOM_MAX = 10000

        /** Extract Advertising Interval (0x1A) from raw data. Unit: 0.625ms */
        fun extractAdvertisingInterval(bytes: ByteArray?): Long? {
            if (bytes == null) return null
            var offset = 0
            while (offset < bytes.size) {
                val length = bytes[offset].toInt() and 0xFF
                if (length == 0) break // End of significant data
                if (offset + 1 + length > bytes.size) break // Malformed

                val type = bytes[offset + 1].toInt() and 0xFF
                if (type == 0x1A && length >= 3) { // 1 byte type + 2 bytes value
                    // Value is 2 bytes LE
                    val v1 = bytes[offset + 2].toInt() and 0xFF
                    val v2 = bytes[offset + 3].toInt() and 0xFF
                    val rawValue = (v2 shl 8) or v1
                    // Unit is 0.625ms
                    return (rawValue * 0.625).toLong()
                }
                offset += 1 + length
            }
            return null
        }
    }

    /** Get all tracked targets. */
    fun getTrackedTargets(): List<TrackedTarget> {
        return targets.values.toList()
    }

    /**
     * Update the name of a tracked target based on external info (e.g. explicit GATT connection).
     * This is crucial for "Shadow Match" logic, as the tracker needs to know the device has a real name
     * even if it only advertises generic names.
     */
    fun updateTargetName(mac: String, name: String) {
        val targetId = macToTarget[mac] ?: return
        val target = targets[targetId] ?: return
        
        if (target.lastDeviceName != name) {
            target.lastDeviceName = name
            Log.i(TAG, "Updated target name from external source: $mac -> $name")
        }
    }

    /** Get targets with high risk score (potential trackers). */
    fun getSuspiciousTargets(minRiskScore: Int = 20): List<TrackedTarget> {
        return targets.values.filter { it.riskScore >= minRiskScore }
    }

    /** Get target by MAC address (including aliases). */
    fun getTargetByMac(mac: String): TrackedTarget? {
        val targetId = macToTarget[mac] ?: return null
        return targets[targetId]
    }

    /** Cleanup old observations. */
    fun cleanup(maxAgeMs: Long = 300_000) {
        val now = System.currentTimeMillis()

        // Remove old observations
        recentObservations.entries.removeIf { (_, obs) -> now - obs.timestamp > maxAgeMs }

        // Remove old targets
        targets.entries.removeIf { (_, target) ->
            val isOld = now - target.lastSeenAt > maxAgeMs
            if (isOld) {
                // Clean up MAC mappings
                target.macAliases.forEach { mac -> macToTarget.remove(mac) }
            }
            isOld
        }

        // Remove old pending
        pendingObservations.entries.removeIf { (_, timestamp) -> now - timestamp > maxAgeMs }
    }

    /**
     * Clear all tracking state. Call this when wiping data.
     */
    fun clear() {
        synchronized(this) {
            targets.clear()
            recentObservations.clear()
            macToTarget.clear()
            pendingObservations.clear()
            isRehydrated = false
            Log.i(TAG, "Tracking state cleared")
        }
    }
}
