package io.blueeye.core.model

/**
 * Lightweight location observation for a single device.
 *
 * Coordinates represent where the phone observed the Bluetooth signal.
 * They must never be interpreted as the exact location of the device.
 */
data class SightingObservation(
    val timestamp: Long,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)
