package io.blueeye.core.data.tracker.model

/** Observation window entry for recent scans. */
data class RecentObservation(
    val mac: String,
    val payloadHash: Int,
    val serviceUuids: Set<String>,
    val deviceName: String?,
    val rssi: Int,
    val timestamp: Long,
    val manufacturerData: ByteArray?,
)
