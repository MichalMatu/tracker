package io.blueeye.feature.details

import io.blueeye.core.model.SightingObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsSightingsReducerTest {
    @Test
    fun `rejects missing poor and invalid location quality`() {
        val summary =
            DetailsSightingsReducer.reduce(
                listOf(
                    sighting(latitude = 51.1, longitude = 17.0, accuracy = null),
                    sighting(latitude = 51.1, longitude = 17.0, accuracy = 101f),
                    sighting(latitude = 91.0, longitude = 17.0, accuracy = 10f),
                    sighting(latitude = 51.1, longitude = Double.NaN, accuracy = 10f),
                    sighting(latitude = 51.1, longitude = 17.0, accuracy = 10f),
                ),
            )

        assertEquals(5, summary.sourceObservationCount)
        assertEquals(1, summary.acceptedObservationCount)
        assertEquals(4, summary.rejectedObservationCount)
        assertEquals(1, summary.clusters.size)
    }

    @Test
    fun `clusters dense observations and uses best real observation as representative`() {
        val first = sighting(timestamp = 1L, latitude = 51.10000, longitude = 17.00000, accuracy = 20f)
        val best = sighting(timestamp = 2L, latitude = 51.10010, longitude = 17.00010, accuracy = 5f)
        val third = sighting(timestamp = 3L, latitude = 51.10020, longitude = 17.00020, accuracy = 15f)

        val summary = DetailsSightingsReducer.reduce(listOf(third, first, best))

        assertEquals(1, summary.clusters.size)
        val cluster = summary.clusters.single()
        assertEquals(3, cluster.observationCount)
        assertEquals(best.latitude, cluster.latitude, 0.0)
        assertEquals(best.longitude, cluster.longitude, 0.0)
        assertEquals(5f, cluster.bestAccuracyMeters)
        assertEquals(20f, cluster.worstAccuracyMeters)
    }

    @Test
    fun `keeps distant locations in separate clusters`() {
        val summary =
            DetailsSightingsReducer.reduce(
                listOf(
                    sighting(latitude = 51.1079, longitude = 17.0385, accuracy = 10f),
                    sighting(latitude = 51.1179, longitude = 17.0385, accuracy = 10f),
                ),
            )

        assertEquals(2, summary.clusters.size)
    }

    @Test
    fun `result is invariant to input order`() {
        val observations =
            listOf(
                sighting(timestamp = 4L, latitude = 51.1079, longitude = 17.0385, accuracy = 10f),
                sighting(timestamp = 2L, latitude = 51.1080, longitude = 17.0385, accuracy = 5f),
                sighting(timestamp = 3L, latitude = 51.1179, longitude = 17.0385, accuracy = 10f),
                sighting(timestamp = 1L, latitude = 51.1180, longitude = 17.0385, accuracy = 6f),
            )

        assertEquals(
            DetailsSightingsReducer.reduce(observations),
            DetailsSightingsReducer.reduce(observations.reversed()),
        )
    }

    @Test
    fun `limits visible clusters and reports omitted clusters honestly`() {
        val observations =
            (0 until 60).map { index ->
                sighting(
                    timestamp = index.toLong(),
                    latitude = 40.0 + (index * 0.01),
                    longitude = 10.0,
                    accuracy = 5f,
                )
            }

        val summary = DetailsSightingsReducer.reduce(observations)

        assertEquals(50, summary.clusters.size)
        assertEquals(10, summary.omittedClusterCount)
        assertEquals(60, summary.acceptedObservationCount)
        assertEquals(50, summary.visibleObservationCount)
        assertTrue(summary.hasUsableLocations)
        assertFalse(summary.clusters.any { it.observationCount > 1 })
    }

    private fun sighting(
        timestamp: Long = 1L,
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        rssi: Int = -60,
    ): SightingObservation =
        SightingObservation(
            timestamp = timestamp,
            rssi = rssi,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
        )
}
