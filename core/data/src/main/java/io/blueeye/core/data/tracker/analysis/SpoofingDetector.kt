package io.blueeye.core.data.tracker.analysis

import io.blueeye.core.data.tracker.fingerprint.KnownDeviceFingerprints
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects BLE fingerprint-spam anomalies (e.g., Flipper Zero "BLE Spam", "Sour Apple") by analyzing
 * the frequency and variety of "Known Device" fingerprints appearing in the environment.
 *
 * If too many *different* high-profile devices (Airpods, Pixel Buds, etc.) appear
 * within a short window, it flags the signal as anomalous rather than confirmed malicious activity.
 */
@Singleton
class SpoofingDetector @Inject constructor() {

    companion object {
        private const val DETECTION_WINDOW_MS = 15_000L
        private const val UNIQUE_DEVICE_THRESHOLD = 20
    }

    private val detectionHistory = ArrayDeque<Pair<Long, String>>()

    var hasFingerprintAnomaly: Boolean = false
        private set

    /**
     * Report a newly scanned packet to the detector.
     * Should be called for every scan result that has successfully matched a [KnownDeviceFingerprints] entry.
     *
     * @param timestampMs Current time in ms
     * @param identifiedModel The model name identified by KnownDeviceFingerprints
     * @return true if this packet crossed the anomaly threshold
     */
    fun onDeviceIdentified(timestampMs: Long, identifiedModel: String): Boolean {
        pruneHistory(timestampMs)
        detectionHistory.add(timestampMs to identifiedModel)

        val uniqueModels = detectionHistory.map { it.second }.toSet()
        val wasAnomalous = hasFingerprintAnomaly

        // A single user might have 2-3 devices (Phone, Buds, Watch).
        // But 5+ distinct types (Pixel Buds + Airpods Pro + Bose + Sony...) in 15s is highly unlikely naturally.
        // Also, simply seeing 20 "Airpods Pro" advertisements from different MACs (if spoofed) is suspicious,
        // but harder to distinguish from a busy train station.
        // We focus on *variety* which is characteristic of "Kitchen Sink" / "Random" spam modes.
        hasFingerprintAnomaly = uniqueModels.size >= UNIQUE_DEVICE_THRESHOLD

        return hasFingerprintAnomaly && !wasAnomalous
    }

    private fun pruneHistory(now: Long) {
        while (detectionHistory.isNotEmpty() && (now - detectionHistory.first().first) > DETECTION_WINDOW_MS) {
            detectionHistory.removeFirst()
        }

        if (detectionHistory.isEmpty()) {
            hasFingerprintAnomaly = false
        } else {
            val uniqueModels = detectionHistory.map { it.second }.toSet()
            if (uniqueModels.size < UNIQUE_DEVICE_THRESHOLD) {
                hasFingerprintAnomaly = false
            }
        }
    }

    fun getAnomalyDetails(): String {
        if (!hasFingerprintAnomaly) return "None"
        val uniqueModels = detectionHistory.map { it.second }.toSet()
        val eventCount = detectionHistory.size
        val uniqueCount = uniqueModels.size
        return buildString {
            append("Fingerprint anomaly: ")
            append(eventCount)
            append(" events, ")
            append(uniqueCount)
            append(" unique known-device models (")
            append(uniqueModels)
            append(")")
        }
    }
}
