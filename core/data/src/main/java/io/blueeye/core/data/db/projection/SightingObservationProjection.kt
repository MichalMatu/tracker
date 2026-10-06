package io.blueeye.core.data.db.projection

data class SightingObservationProjection(
    val timestamp: Long,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracy: Float?,
)
