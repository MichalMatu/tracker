package io.blueeye.feature.details

import io.blueeye.core.model.SightingObservation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

data class DetailsSightingCluster(
    val latitude: Double,
    val longitude: Double,
    val observationCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val bestAccuracyMeters: Float,
    val worstAccuracyMeters: Float,
    val averageRssi: Double,
)

data class DetailsSightingsSummary(
    val clusters: List<DetailsSightingCluster>,
    val sourceObservationCount: Int,
    val acceptedObservationCount: Int,
    val visibleObservationCount: Int,
    val rejectedObservationCount: Int,
    val omittedClusterCount: Int,
    val firstSeenAt: Long?,
    val lastSeenAt: Long?,
) {
    val hasUsableLocations: Boolean
        get() = clusters.isNotEmpty()
}

object DetailsSightingsReducer {
    fun reduce(observations: List<SightingObservation>): DetailsSightingsSummary {
        val usable =
            observations
                .asSequence()
                .filter(::isUsable)
                .sortedWith(OBSERVATION_ORDER)
                .toList()

        val mutableClusters = mutableListOf<MutableSightingCluster>()
        usable.forEach { observation ->
            val target = findTargetCluster(observation, mutableClusters)
            if (target == null) {
                mutableClusters += MutableSightingCluster(observation)
            } else {
                target.add(observation)
            }
        }

        val allClusters =
            mutableClusters
                .map(MutableSightingCluster::toDomain)
                .sortedWith(CLUSTER_ORDER)
        val visibleClusters = allClusters.take(MAX_VISIBLE_CLUSTERS)

        return DetailsSightingsSummary(
            clusters = visibleClusters,
            sourceObservationCount = observations.size,
            acceptedObservationCount = usable.size,
            visibleObservationCount = visibleClusters.sumOf(DetailsSightingCluster::observationCount),
            rejectedObservationCount = observations.size - usable.size,
            omittedClusterCount = allClusters.size - visibleClusters.size,
            firstSeenAt = usable.minOfOrNull(SightingObservation::timestamp),
            lastSeenAt = usable.maxOfOrNull(SightingObservation::timestamp),
        )
    }

    private fun isUsable(observation: SightingObservation): Boolean {
        val accuracy = observation.accuracyMeters ?: return false
        return observation.latitude.isFinite() &&
            observation.longitude.isFinite() &&
            accuracy.isFinite() &&
            observation.latitude in MIN_LATITUDE..MAX_LATITUDE &&
            observation.longitude in MIN_LONGITUDE..MAX_LONGITUDE &&
            accuracy > MIN_ACCURACY_METERS &&
            accuracy <= MAX_ACCURACY_METERS
    }

    private fun findTargetCluster(
        observation: SightingObservation,
        clusters: List<MutableSightingCluster>,
    ): MutableSightingCluster? =
        clusters
            .asSequence()
            .map { cluster -> cluster to distanceMeters(observation, cluster.representative) }
            .filter { (cluster, distance) ->
                distance <= mergeRadiusMeters(observation, cluster)
            }
            .minWithOrNull(
                compareBy<Pair<MutableSightingCluster, Double>> { it.second }
                    .thenBy { it.first.representative.timestamp }
                    .thenBy { it.first.representative.latitude }
                    .thenBy { it.first.representative.longitude },
            )
            ?.first

    private fun mergeRadiusMeters(
        observation: SightingObservation,
        cluster: MutableSightingCluster,
    ): Double =
        max(
            MIN_CLUSTER_RADIUS_METERS,
            max(
                observation.accuracyMeters!!.toDouble(),
                cluster.bestAccuracyMeters.toDouble(),
            ),
        )

    private fun distanceMeters(
        first: SightingObservation,
        second: SightingObservation,
    ): Double {
        val firstLat = first.latitude.toRadians()
        val secondLat = second.latitude.toRadians()
        val deltaLat = secondLat - firstLat
        val deltaLon = (second.longitude - first.longitude).toRadians()
        val x = deltaLon * cos((firstLat + secondLat) / 2.0)
        return EARTH_RADIUS_METERS * sqrt((x * x) + (deltaLat * deltaLat))
    }

    private fun Double.toRadians(): Double = this * PI / HALF_CIRCLE_DEGREES

    private val OBSERVATION_ORDER =
        compareBy<SightingObservation>(
            SightingObservation::timestamp,
            SightingObservation::latitude,
            SightingObservation::longitude,
            SightingObservation::rssi,
        )

    private val CLUSTER_ORDER =
        compareByDescending<DetailsSightingCluster>(DetailsSightingCluster::lastSeenAt)
            .thenByDescending(DetailsSightingCluster::observationCount)
            .thenBy(DetailsSightingCluster::latitude)
            .thenBy(DetailsSightingCluster::longitude)

    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_ACCURACY_METERS = 0f
    private const val MAX_ACCURACY_METERS = 100f
    private const val MIN_CLUSTER_RADIUS_METERS = 50.0
    private const val MAX_VISIBLE_CLUSTERS = 50
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val HALF_CIRCLE_DEGREES = 180.0
}

private class MutableSightingCluster(initial: SightingObservation) {
    var representative: SightingObservation = initial
        private set
    var observationCount: Int = 1
        private set
    var firstSeenAt: Long = initial.timestamp
        private set
    var lastSeenAt: Long = initial.timestamp
        private set
    var bestAccuracyMeters: Float = requireNotNull(initial.accuracyMeters)
        private set
    private var worstAccuracyMeters: Float = bestAccuracyMeters
    private var rssiTotal: Long = initial.rssi.toLong()

    fun add(observation: SightingObservation) {
        val accuracy = requireNotNull(observation.accuracyMeters)
        observationCount += 1
        firstSeenAt = minOf(firstSeenAt, observation.timestamp)
        lastSeenAt = maxOf(lastSeenAt, observation.timestamp)
        bestAccuracyMeters = minOf(bestAccuracyMeters, accuracy)
        worstAccuracyMeters = maxOf(worstAccuracyMeters, accuracy)
        rssiTotal += observation.rssi
        if (isBetterRepresentative(observation, representative)) {
            representative = observation
        }
    }

    fun toDomain(): DetailsSightingCluster =
        DetailsSightingCluster(
            latitude = representative.latitude,
            longitude = representative.longitude,
            observationCount = observationCount,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            bestAccuracyMeters = bestAccuracyMeters,
            worstAccuracyMeters = worstAccuracyMeters,
            averageRssi = rssiTotal.toDouble() / observationCount,
        )

    private fun isBetterRepresentative(
        candidate: SightingObservation,
        current: SightingObservation,
    ): Boolean {
        val candidateAccuracy = requireNotNull(candidate.accuracyMeters)
        val currentAccuracy = requireNotNull(current.accuracyMeters)
        return when {
            candidateAccuracy < currentAccuracy -> true
            candidateAccuracy > currentAccuracy -> false
            candidate.timestamp > current.timestamp -> true
            candidate.timestamp < current.timestamp -> false
            candidate.latitude < current.latitude -> true
            candidate.latitude > current.latitude -> false
            else -> candidate.longitude < current.longitude
        }
    }
}
