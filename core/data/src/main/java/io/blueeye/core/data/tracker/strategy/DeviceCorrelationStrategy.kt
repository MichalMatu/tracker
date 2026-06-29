package io.blueeye.core.data.tracker.strategy

import io.blueeye.core.data.classifier.AppleIdentityConflictGuard
import io.blueeye.core.data.tracker.model.CarryoverMatch
import io.blueeye.core.data.tracker.model.CarryoverMatchEvidence
import io.blueeye.core.data.tracker.model.CarryoverMatchReason
import io.blueeye.core.data.tracker.model.TrackedTarget
import io.blueeye.core.scanner.model.BleScanResultData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Strategy for correlating a new scan result with existing tracked targets. Encapsulates the
 * heuristics for detecting device carryover (MAC address rotation).
 */
@Singleton
class DeviceCorrelationStrategy
@Inject
constructor() {
    companion object {
        /** Maximum time to consider for carryover (30 seconds) */
        const val CARRYOVER_WINDOW_MS = 30_000L

        /** Maximum RSSI difference for correlation */
        const val MAX_RSSI_DIFF = 25

        /** Correlation Threshold (0.0 - 1.0) - above this, we consider it a match */
        const val MATCH_THRESHOLD = 0.75f

        // Weights
        private const val WEIGHT_PAYLOAD = 0.6f
        private const val WEIGHT_INTERVAL = 0.4f
        private const val WEIGHT_UUID = 0.1f
        private const val WEIGHT_NAME = 0.2f
        private const val PERCENT_MULTIPLIER = 100
        private const val MAX_SUMMARY_VALUE_LENGTH = 64
        private const val EXTREME_RSSI_DIFF = 50
        private const val RSSI_PENALTY_MULTIPLIER = 0.5f

        // Known Vendor Headers to strip (Little Endian Manufacturer ID)
        private val HEADER_APPLE = byteArrayOf(0x4C.toByte(), 0x00.toByte())
        private val HEADER_MICROSOFT = byteArrayOf(0x06.toByte(), 0x00.toByte())
    }

    /**
     * Find a potential carryover match using Weighted Fuzzy Matching & Signal Analysis.
     *
     * @return Target match and evidence if a match is found, null otherwise.
     */
    fun findMatch(
        data: BleScanResultData,
        deviceName: String?,
        advertisingInterval: Long?,
        targets: Collection<TrackedTarget>,
    ): CarryoverMatch? {
        val input =
            MatchInput(
                data = data,
                deviceName = deviceName,
                advertisingInterval = advertisingInterval,
                serviceUuids = data.serviceUuids.toSet(),
                rawData = data.rawData ?: data.manufacturerData,
            )

        // Select best target based on score
        var bestMatch: CarryoverMatch? = null
        var highestScore = 0f

        for (target in targets) {
            if (AppleIdentityConflictGuard.hasNameFamilyConflict(input.deviceName, target.lastDeviceName)) {
                continue
            }
            val timeSinceLastSeen = input.data.timestamp - target.lastSeenAt
            if (timeSinceLastSeen <= CARRYOVER_WINDOW_MS) {
                immediateMatchForTarget(input, target)?.let { return it }
                val weightedMatch = weightedMatchForTarget(input, target)
                if (weightedMatch != null && weightedMatch.evidence.confidence > highestScore) {
                    highestScore = weightedMatch.evidence.confidence
                    bestMatch = weightedMatch
                }
            }
        }

        return bestMatch
    }

    private fun weightedMatchForTarget(
        input: MatchInput,
        target: TrackedTarget,
    ): CarryoverMatch? {
        val strippedRawData = stripHeaders(input.rawData)
        val strippedTargetPayload = stripHeaders(target.lastPayload)
        val scorePayload = matchPayloadFuzzy(strippedRawData, strippedTargetPayload)
        val scores =
            FeatureScores(
                name = matchName(input.deviceName, target.lastDeviceName),
                uuid = matchUuids(input.serviceUuids, target.lastServiceUuids),
                payload = scorePayload,
                sequence =
                    if (scorePayload in 0.8f..0.99f) {
                        matchSequenceHeuristic(strippedRawData, strippedTargetPayload)
                    } else {
                        0f
                    },
                interval = matchInterval(input.advertisingInterval, target.lastAdvertisingInterval),
            )
        val normalizedScore =
            weightedScore(
                input = input,
                target = target,
                scores = scores,
            ).withRssiPenalty(input, target, scores.name)

        if (normalizedScore <= MATCH_THRESHOLD) return null

        return CarryoverMatch(
            targetId = target.targetId,
            evidence =
                carryoverEvidence(
                    reasonCode = CarryoverMatchReason.WEIGHTED_FEATURE_MATCH,
                    confidence = normalizedScore,
                    input = input,
                    target = target,
                    details =
                        "scorePct=${normalizedScore.toPercent()};" +
                            "namePct=${scores.name.toPercent()};" +
                            "uuidPct=${scores.uuid.toPercent()};" +
                            "payloadPct=${scores.finalPayload.toPercent()};" +
                            "sequencePct=${scores.sequence.toPercent()};" +
                            "intervalPct=${scores.interval.toPercent()}",
                ),
        )
    }

    private fun weightedScore(
        input: MatchInput,
        target: TrackedTarget,
        scores: FeatureScores,
    ): Float {
        var currentScore = 0f
        var maxPossibleScore = 0f

        if (!input.deviceName.isNullOrBlank() && !target.lastDeviceName.isNullOrBlank()) {
            currentScore += scores.name * WEIGHT_NAME
            maxPossibleScore += WEIGHT_NAME
        }

        if (input.serviceUuids.isNotEmpty() && target.lastServiceUuids.isNotEmpty()) {
            currentScore += scores.uuid * WEIGHT_UUID
            maxPossibleScore += WEIGHT_UUID
        }

        if (input.rawData != null || target.lastPayload != null) {
            currentScore += scores.finalPayload * WEIGHT_PAYLOAD
            maxPossibleScore += WEIGHT_PAYLOAD
        }

        if (input.advertisingInterval != null && target.lastAdvertisingInterval != null) {
            currentScore += scores.interval * WEIGHT_INTERVAL
            maxPossibleScore += WEIGHT_INTERVAL
        }

        return if (maxPossibleScore > 0f) currentScore / maxPossibleScore else 0f
    }

    private fun Float.withRssiPenalty(
        input: MatchInput,
        target: TrackedTarget,
        scoreName: Float,
    ): Float {
        val rssiDiff = abs(input.data.rssi - target.lastRssi)
        if (rssiDiff <= MAX_RSSI_DIFF) return this

        val isVeryExtreme = rssiDiff > EXTREME_RSSI_DIFF
        val isGenericNameMatch = scoreName > 0.9f && isGenericName(input.deviceName)
        val shouldPenalize = this < 1.0f || isVeryExtreme || isGenericNameMatch

        return if (shouldPenalize) this * RSSI_PENALTY_MULTIPLIER else this
    }

    private fun immediateMatchForTarget(
        input: MatchInput,
        target: TrackedTarget,
    ): CarryoverMatch? {
        val data = input.data
        val reasonCode =
            when {
                matchShadow(data, target) >= 1.0f -> CarryoverMatchReason.APPLE_SHADOW
                matchMicrosoft(data, target) >= 1.0f -> CarryoverMatchReason.MICROSOFT_SHADOW
                matchSameNameHeuristic(data, target) >= 1.0f -> CarryoverMatchReason.SAME_NAME_PROXIMITY
                else -> null
            } ?: return null

        return CarryoverMatch(
            targetId = target.targetId,
            evidence =
                carryoverEvidence(
                    reasonCode = reasonCode,
                    confidence = 1.0f,
                    input = input,
                    target = target,
                    details = "scorePct=100",
                ),
        )
    }

    private fun carryoverEvidence(
        reasonCode: CarryoverMatchReason,
        confidence: Float,
        input: MatchInput,
        target: TrackedTarget,
        details: String,
    ): CarryoverMatchEvidence {
        val data = input.data
        val rssiDiff = abs(data.rssi - target.lastRssi)
        val timeDeltaMs = data.timestamp - target.lastSeenAt
        return CarryoverMatchEvidence(
            reasonCode = reasonCode,
            confidence = confidence.coerceIn(0f, 1f),
            featureSummary =
                "$details;" +
                    "rssiDiff=$rssiDiff;" +
                    "timeDeltaMs=$timeDeltaMs;" +
                    "observedName=${input.deviceName.summaryValue()};" +
                    "targetName=${target.lastDeviceName.summaryValue()}",
        )
    }

    private data class MatchInput(
        val data: BleScanResultData,
        val deviceName: String?,
        val advertisingInterval: Long?,
        val serviceUuids: Set<String>,
        val rawData: ByteArray?,
    )

    private data class FeatureScores(
        val name: Float,
        val uuid: Float,
        val payload: Float,
        val sequence: Float,
        val interval: Float,
    ) {
        val finalPayload: Float = if (sequence > 0.5f) 1.0f else payload
    }

    private fun Float.toPercent(): Int = (coerceIn(0f, 1f) * PERCENT_MULTIPLIER).roundToInt()

    private fun String?.summaryValue(): String =
        this
            ?.takeIf { it.isNotBlank() }
            ?.replace(';', ',')
            ?.replace('=', ':')
            ?.take(MAX_SUMMARY_VALUE_LENGTH)
            ?: "none"

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

    private fun matchInterval(intervalA: Long?, intervalB: Long?): Float {
        if (intervalA == null || intervalB == null) return 0f
        // Allow 10% drift
        val diff = abs(intervalA - intervalB)
        val limit = intervalA * 0.1
        return if (diff < limit) 1.0f else 0f
    }

    /**
     * "Shadow Match" Heuristic for Apple Devices:
     * If we see a "Shadow" signal (generic name) very close (Time & RSSI) to a "Main" signal (named device)
     * and both are Apple (0x004C), we assume they are the same physical device emitting different packets.
     */
    private fun matchShadow(
        data: BleScanResultData,
        target: TrackedTarget
    ): Float {
        // 1. Vendor Check: Must be Apple (0x004C = 76)
        val isAppleInfo = data.manufacturerId == 76 ||
            hasAppleManufacturerData(data.manufacturerData) ||
            hasAppleManufacturerData(data.rawData)
        
        if (!isAppleInfo) return 0f

        val targetIsAppleInfo =
            hasAppleManufacturerData(target.lastPayload) ||
                isAppleDeviceName(target.lastDeviceName)

        if (!targetIsAppleInfo) return 0f

        // 2. Shadow Identification
        val dataIsShadow = isShadowName(data.name)
        val targetIsShadow = isShadowName(target.lastDeviceName)

        // Rule: At least ONE of them must be a Shadow to be a Shadow Match.
        // - Shadow -> Main (Classic flow)
        // - Main -> Shadow (Order Independence / "Upgrade" flow)
        // - Shadow -> Shadow (Merge concurrent generic packets)
        if (!dataIsShadow && !targetIsShadow) return 0f

        // 3. Signal Strength Check
        val rssiDiff = abs(data.rssi - target.lastRssi)
        
        // RELAXED THRESHOLDS:
        // Strong signals (> -75): Allow diff up to 20 (was 15).
        // Weak signals (< -75): Allow diff up to 10 (was 4).
        val isWeak = data.rssi < -75
        val limit = if (isWeak) 10 else 20
        
        // CONCURRENT BOOST: If signals are simultaneous (< 1s) and strong, we assume same source (multi-service).
        // User observed ~4-5dB diff. We allow up to 8dB for concurrent strong signals (Tightened).
        val timeDiff = abs(data.timestamp - target.lastSeenAt)
        if (timeDiff < 1000 && !isWeak && rssiDiff <= 8) {
             android.util.Log.i("ShadowMatch", "Concurrent Apple Merge (${if(dataIsShadow) "S->T" else "T->S"}): ${data.mac} -> ${target.primaryMac} (dt=${timeDiff}ms, dRsum=${rssiDiff})")
             return 1.0f
        }

        if (rssiDiff > limit) {
             return 0f
        }

        // 4. Match!
        android.util.Log.i("ShadowMatch", "Shadow MATCH: ${data.mac} (${data.name}) -> ${target.primaryMac} (${target.lastDeviceName}). RSSI: ${data.rssi}/${target.lastRssi}")
        return 1.0f
    }

    /**
     * "Microsoft Shadow Match" - merges multiple Windows 10 Desktop beacons (rotating MACs)
     * if they are physically close.
     */
    private fun matchMicrosoft(
        data: BleScanResultData,
        target: TrackedTarget
    ): Float {
        // 1. Vendor Check: Must be Microsoft (0x0006)
        val isMsInfo = data.manufacturerId == 6 || 
                       (data.manufacturerData != null && data.manufacturerData.size >= 2 && 
                        data.manufacturerData[0] == 0x06.toByte() && data.manufacturerData[1] == 0x00.toByte())
        
        if (!isMsInfo) return 0f

        // 2. Target Check (Target should have Microsoft vendor or "Windows" name)
        // If target was already classified as Windows, its vendor might be "Microsoft".
        // Search in name safely
        val targetName = target.lastDeviceName
        var targetIsWindows = targetName?.contains("Windows", ignoreCase = true) == true
        
        // Also check if target's last payload has Microsoft ID if name is missing?
        // For now, rely on Name or if target is also emitting MS data (we could parse lastPayload but let's keep it simple)
        
        if (!targetIsWindows) {
            // Check payload for 0x06 00
            val payload = target.lastPayload
            if (payload != null && payload.size >= 4) {
                 // Check for 0x06 00 in typical offsets. 
                 // Often AD Structure: [Len][FF][06][00]...
                 // Let's brute force search 06 00 ? No, strict parsing is better but complex here.
                 // Let's assume if it has no name, we might skip it unless strictly matching payload.
                 return 0f
            }
            return 0f
        }

        // 3. Signal Strength & Proximity
        val rssiDiff = abs(data.rssi - target.lastRssi)
        
        // Similar to Apple: Allow weak signals if very close
        val isWeak = data.rssi < -75
        val isVeryClose = rssiDiff <= 3
        
        if ((isWeak && !isVeryClose) || rssiDiff > 10) {
             return 0f
        }

        android.util.Log.i("ShadowMatch", "Microsoft MATCH: ${data.mac} -> ${target.primaryMac}. RSSI: ${data.rssi}/${target.lastRssi}")
        return 1.0f
    }

    private fun matchSameNameHeuristic(
        data: BleScanResultData,
        target: TrackedTarget
    ): Float {
        // 1. Must have same non-empty name
        if (data.name.isNullOrBlank() || target.lastDeviceName.isNullOrBlank()) {
             // android.util.Log.v("ShadowMatch", "Same Name SKIP: Name missing. '${data.name}' vs '${target.lastDeviceName}'")
             return 0f
        }
        
        if (data.name != target.lastDeviceName) {
             // Log only if both are Apple-ish to reduce noise
             // android.util.Log.v("ShadowMatch", "Same Name SKIP: '$data.name' != '${target.lastDeviceName}'")
             return 0f
        }
        
        // 2. Correlation Strength Check
        // If Apple, we trust it more easily.
        // If NOT Apple, we only merge if the name is "Specific" (not "iPhone", "TV", etc.)
        val isAppleInfo = data.manufacturerId == 76 || 
                          (data.manufacturerData != null && data.manufacturerData.size >= 2 && 
                           data.manufacturerData[0] == 0x4C.toByte() && data.manufacturerData[1] == 0x00.toByte())

        val isSafeName = isAppleInfo || !isGenericName(data.name)
        
        if (!isSafeName) return 0f

        // 3. Must be strong signals and close
        val rssiDiff = abs(data.rssi - target.lastRssi)
        
        // Relaxed MAX: Allow diff <= 20 (was 15) and RSSI > -95 (was -90)
        // Same Name is a very strong signal of identity, so we can trust spatial locality more loosely.
        if (rssiDiff <= 20 && data.rssi > -95) {
            android.util.Log.i("ShadowMatch", "Same Name MATCH: ${data.mac} (${data.name}) -> ${target.primaryMac}. RSSI: ${data.rssi}/${target.lastRssi}")
            return 1.0f
        } else {
             android.util.Log.v("ShadowMatch", "Same Name REJECT: ${data.mac} vs ${target.primaryMac}. Name '${data.name}'. RSSI: ${data.rssi}/${target.lastRssi} (Diff: $rssiDiff)")
        }
        
        return 0f
    }

    private fun isGenericName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val lower = name.lowercase()
        return lower in setOf(
            "iphone", "ipad", "ipod", "macbook", "macbook air", "macbook pro", "apple watch",
            "samsung", "galaxy", "oneplus", "oppo", "xiaomi", "redmi", "huawei", "android",
            "tv", "smart tv", "headphones", "headset", "earbuds", "speaker",
            "computer", "desktop", "laptop", "tablet", "phone", "watch",
            "le", "le device", "unknown", "accessory", "genericdevice"
        )
    }

    private fun isShadowName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val lower = name.lowercase()
        return lower.startsWith("apple inc") || 
               lower.startsWith("apple, inc") || // Handle comma
               lower == "apple device" || 
               lower.startsWith("find my") || 
               lower == "continuity" || 
               lower == "unknown" ||
               lower == "call handover" || 
               lower == "nearby"
    }

    private fun isAppleDeviceName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.startsWith("apple") ||
            lower.startsWith("iphone") ||
            lower.startsWith("ipad") ||
            lower.startsWith("ipod") ||
            lower.startsWith("mac") ||
            lower.startsWith("homepod") ||
            lower.startsWith("airpods") ||
            lower.startsWith("find my")
    }

    private fun hasAppleManufacturerData(payload: ByteArray?): Boolean {
        if (payload == null || payload.size < 2) return false
        if (payload[0] == HEADER_APPLE[0] && payload[1] == HEADER_APPLE[1]) return true

        var offset = 0
        while (offset < payload.size) {
            val length = payload[offset].toInt() and 0xFF
            if (length == 0) break
            if (offset + 1 + length > payload.size) break

            val type = payload[offset + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 3) {
                val c1 = payload[offset + 2]
                val c2 = payload[offset + 3]
                if (c1 == HEADER_APPLE[0] && c2 == HEADER_APPLE[1]) return true
            }
            offset += 1 + length
        }

        return false
    }

    /**
     * Heuristic for Sequence Numbers:
     * If payloads are identical size and have very few bit flips (Hamming Distance <= 2), 
     * it's extremely likely to be the same device incrementing a counter.
     */
    private fun matchSequenceHeuristic(a: ByteArray?, b: ByteArray?): Float {
        if (a == null || b == null) return 0f
        if (a.size != b.size) return 0f
        
        var bitDiff = 0
        for (i in a.indices) {
            val diff = a[i].toInt() xor b[i].toInt()
            if (diff != 0) {
                bitDiff += Integer.bitCount(diff)
            }
            if (bitDiff > 2) return 0f // Too many changes
        }
        
        // If 1 or 2 bits changed, high likelihood of sequence number
        return if (bitDiff in 1..2) 1.0f else 0f
    }

    private fun stripHeaders(payload: ByteArray?): ByteArray? {
        if (payload == null || payload.size < 3) return payload
        
        // Scan for Manufacturer Data (0xFF)
        // Simple heuristic: If payload starts with 0xFF (after length), strip the next 2 bytes.
        // But rawData has [Len][Type][Value...]. We need to parse.
        // Since we are doing "fuzzy matching" on the WHOLE blob, stripping just the FIRST company ID is good enough for now.
        // Iterate AD structures
        var offset = 0
        while (offset < payload.size) {
            val length = payload[offset].toInt() and 0xFF
            if (length == 0) break
            if (offset + 1 + length > payload.size) break

            val type = payload[offset + 1].toInt() and 0xFF
            // Manufacturer Specific Data
            if (type == 0xFF && length >= 3) {
                // Check Company ID (Little Endian)
                val c1 = payload[offset + 2]
                val c2 = payload[offset + 3]
                
                // Check for Apple (4C 00) or Microsoft (06 00)
                val isApple = c1 == HEADER_APPLE[0] && c2 == HEADER_APPLE[1]
                val isMs = c1 == HEADER_MICROSOFT[0] && c2 == HEADER_MICROSOFT[1]

                if (isApple || isMs) {
                    // Return payload WITHOUT this Company ID (3 bytes: Type FF + C1 + C2 replaced by... nothing? 
                    // No, we want to match the *rest* of the data.
                    // Let's return a copy with the Company ID bytes zeroed out or removed?
                    // Removal is cleaner.
                    
                    // Actually, we should return the Data part ONLY? 
                    // No, keep other AD structures?
                    // Expert said: "Strip manufacturer headers".
                    // Let's remove the 2 bytes of Company ID.
                    // Copy everything EXCEPT [offset+2, offset+3]
                    
                    return payload.filterIndexed { index, _ -> 
                        index != offset + 2 && index != offset + 3 
                    }.toByteArray()
                }
            }
            offset += 1 + length
        }
        return payload
    }

    private fun matchPayloadFuzzy(payloadA: ByteArray?, payloadB: ByteArray?): Float {
        if (payloadA == null || payloadB == null) return 0f
        if (payloadA.contentEquals(payloadB)) return 1.0f

        // If lengths differ significantly (>20%), unlikely to be same device
        val lenA = payloadA.size
        val lenB = payloadB.size
        if (abs(lenA - lenB) > maxOf(lenA, lenB) * 0.2) return 0f

        // Count matching bytes
        val checkLen = minOf(lenA, lenB)
        var matches = 0
        for (i in 0 until checkLen) {
            if (payloadA[i] == payloadB[i]) {
                matches++
            }
        }
        
        // Return percentage of matches
        return matches.toFloat() / checkLen
    }
}
