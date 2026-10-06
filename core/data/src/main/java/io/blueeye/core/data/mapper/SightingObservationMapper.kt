package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.projection.SightingObservationProjection
import io.blueeye.core.model.SightingObservation

fun SightingObservationProjection.toSightingObservation(): SightingObservation? {
    val lat = latitude
    val lon = longitude
    return if (lat != null && lon != null) {
        SightingObservation(
            timestamp = timestamp,
            rssi = rssi,
            latitude = lat,
            longitude = lon,
            accuracyMeters = locationAccuracy,
        )
    } else {
        null
    }
}

fun List<SightingObservationProjection>.toSightingObservations(): List<SightingObservation> =
    mapNotNull(SightingObservationProjection::toSightingObservation)
