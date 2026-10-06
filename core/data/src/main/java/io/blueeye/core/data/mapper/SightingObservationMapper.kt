package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.projection.SightingObservationProjection
import io.blueeye.core.model.SightingObservation

fun SightingObservationProjection.toSightingObservation(): SightingObservation? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return SightingObservation(
        timestamp = timestamp,
        rssi = rssi,
        latitude = lat,
        longitude = lon,
        accuracyMeters = locationAccuracy,
    )
}

fun List<SightingObservationProjection>.toSightingObservations(): List<SightingObservation> =
    mapNotNull(SightingObservationProjection::toSightingObservation)
