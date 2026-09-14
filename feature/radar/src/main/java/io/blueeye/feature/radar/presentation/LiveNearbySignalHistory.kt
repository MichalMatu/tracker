package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.RadarDeviceSummary

internal class LiveNearbySignalHistory {
    private data class Sample(val timestamp: Long, val rssi: Int)

    private val samplesByFingerprint = mutableMapOf<String, MutableList<Sample>>()

    fun observe(
        devices: List<RadarDeviceSummary>,
        nowMs: Long
    ) {
        devices.forEach { device ->
            val samples = samplesByFingerprint.getOrPut(device.fingerprint) { mutableListOf() }
            if (samples.lastOrNull()?.timestamp != device.lastSeenAt) {
                samples += Sample(device.lastSeenAt, device.rssi)
            }
        }
        val cutoff = nowMs - HISTORY_WINDOW_MS
        samplesByFingerprint.entries.removeAll { (_, samples) ->
            samples.removeAll { sample -> sample.timestamp < cutoff }
            samples.isEmpty()
        }
    }

    fun smoothedRssi(device: RadarDeviceSummary): Int {
        val values = samplesByFingerprint[device.fingerprint]?.map { it.rssi }.orEmpty()
        return median(values).takeIf { values.isNotEmpty() } ?: device.rssi
    }

    fun trend(
        fingerprint: String,
        nowMs: Long
    ): LiveSignalTrend {
        val cutoff = nowMs - TREND_WINDOW_MS
        val values =
            samplesByFingerprint[fingerprint]
                .orEmpty()
                .filter { it.timestamp >= cutoff }
                .map { it.rssi }
        if (values.size < MIN_TREND_SAMPLES) return LiveSignalTrend.UNKNOWN

        val midpoint = values.size / 2
        val first = median(values.take(midpoint))
        val second = median(values.drop(midpoint))
        val delta = second - first
        return when {
            delta >= TREND_THRESHOLD_DB -> LiveSignalTrend.STRONGER
            delta <= -TREND_THRESHOLD_DB -> LiveSignalTrend.WEAKER
            else -> LiveSignalTrend.STEADY
        }
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val HISTORY_WINDOW_MS = 30_000L
        const val TREND_WINDOW_MS = 15_000L
        const val MIN_TREND_SAMPLES = 4
        const val TREND_THRESHOLD_DB = 6
    }
}
