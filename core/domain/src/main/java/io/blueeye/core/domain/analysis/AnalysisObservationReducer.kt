package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.analysis.AnalysisAlertSummaryV1
import io.blueeye.core.model.analysis.AnalysisEncounterSegmentV1
import io.blueeye.core.model.analysis.AnalysisLocationQualityV1
import io.blueeye.core.model.analysis.AnalysisMovementSummaryV1
import io.blueeye.core.model.analysis.AnalysisRssiSummaryV1
import io.blueeye.core.model.analysis.AnalysisSignalBucketV1
import io.blueeye.core.model.analysis.AnalysisSignalSummaryV1

internal object AnalysisObservationReducer {
    fun signal(samples: List<SignalSample>): AnalysisSignalSummaryV1 {
        val canonical = canonicalSignalSamples(samples)
        val usableLocationSamples = canonical.filter(::hasUsableLocation)
        val samplesWithCoordinates = canonical.count(::hasCoordinates)

        return AnalysisSignalSummaryV1(
            sourceSampleCount = samples.size,
            uniqueSampleCount = canonical.size,
            rssi =
                AnalysisRssiSummaryV1(
                    sampleCount = canonical.size,
                    minimum = canonical.minOfOrNull(SignalSample::rssi),
                    maximum = canonical.maxOfOrNull(SignalSample::rssi),
                    average = canonical.averageRssi(),
                ),
            location =
                AnalysisLocationQualityV1(
                    samplesWithCoordinates = samplesWithCoordinates,
                    usableSamples = usableLocationSamples.size,
                    poorSamples = samplesWithCoordinates - usableLocationSamples.size,
                    bestUsableAccuracyMeters =
                        usableLocationSamples.mapNotNull(SignalSample::locationAccuracy).minOrNull(),
                    worstUsableAccuracyMeters =
                        usableLocationSamples.mapNotNull(SignalSample::locationAccuracy).maxOrNull(),
                ),
            buckets = canonical.toBuckets(),
        )
    }

    fun movement(samples: List<FollowMeHistorySample>): AnalysisMovementSummaryV1 {
        val canonical =
            samples
                .distinct()
                .sortedWith(
                    compareBy<FollowMeHistorySample>(
                        FollowMeHistorySample::timestamp,
                        FollowMeHistorySample::observedMac,
                        FollowMeHistorySample::encounterCount,
                        FollowMeHistorySample::score,
                    ),
                )

        return AnalysisMovementSummaryV1(
            sourceSampleCount = samples.size,
            maxScore = canonical.maxOfOrNull(FollowMeHistorySample::score),
            maxEncounterCount = canonical.maxOfOrNull(FollowMeHistorySample::encounterCount),
            userMovedSampleCount = canonical.count { it.userMoved == true },
            baselineSampleCount = canonical.count { it.baselineDevice == true },
            segments = canonical.toEncounterSegments(),
        )
    }

    fun alerts(events: List<AlertEvidenceEvent>): AnalysisAlertSummaryV1 {
        val canonical =
            events
                .distinct()
                .sortedWith(
                    compareBy<AlertEvidenceEvent>(
                        AlertEvidenceEvent::timestamp,
                        { it.eventType.name },
                        { it.evidence.source.name },
                        { it.evidence.reasonText },
                    ),
                )

        return AnalysisAlertSummaryV1(
            totalCount = canonical.size,
            watchlistReturnCount = canonical.count { it.eventType == AlertEvidenceEventType.WATCHLIST_RETURN },
            publicSafetyCount = canonical.count { it.eventType == AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL },
            followMeAlertCount = canonical.count { it.eventType == AlertEvidenceEventType.FOLLOW_ME_ALERT },
            firstAlertAt = canonical.firstOrNull()?.timestamp,
            lastAlertAt = canonical.lastOrNull()?.timestamp,
        )
    }

    private fun canonicalSignalSamples(samples: List<SignalSample>): List<SignalSample> =
        samples
            .sortedWith(
                compareBy<SignalSample>(
                    SignalSample::timestamp,
                    SignalSample::rssi,
                    SignalSample::observedMac,
                    SignalSample::latitude,
                    SignalSample::longitude,
                    SignalSample::locationAccuracy,
                ),
            )
            .distinctBy { sample ->
                AnalysisSignalIdentity(
                    timestamp = sample.timestamp,
                    rssi = sample.rssi,
                    observedMac = sample.observedMac,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    locationAccuracy = sample.locationAccuracy,
                )
            }

    private fun List<SignalSample>.toBuckets(): List<AnalysisSignalBucketV1> =
        groupBy { sample -> bucketStart(sample.timestamp) }
            .toSortedMap()
            .map { (bucketStartedAt, bucketSamples) ->
                AnalysisSignalBucketV1(
                    bucketStartedAt = bucketStartedAt,
                    sampleCount = bucketSamples.size,
                    minimumRssi = bucketSamples.minOf(SignalSample::rssi),
                    maximumRssi = bucketSamples.maxOf(SignalSample::rssi),
                    averageRssi = requireNotNull(bucketSamples.averageRssi()),
                    usableLocationSampleCount = bucketSamples.count(::hasUsableLocation),
                )
            }

    private fun List<FollowMeHistorySample>.toEncounterSegments(): List<AnalysisEncounterSegmentV1> {
        if (isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<FollowMeHistorySample>>()
        forEach { sample ->
            val current = groups.lastOrNull()
            val previous = current?.lastOrNull()
            if (previous == null || startsNewSegment(previous, sample)) {
                groups += mutableListOf(sample)
            } else {
                current += sample
            }
        }

        return groups.map { group ->
            AnalysisEncounterSegmentV1(
                startedAt = group.first().timestamp,
                endedAt = group.last().timestamp,
                sampleCount = group.size,
                maxScore = group.maxOf(FollowMeHistorySample::score),
                maxEncounterCount = group.maxOf(FollowMeHistorySample::encounterCount),
                userMovedObserved = group.any { it.userMoved == true },
            )
        }
    }

    private fun startsNewSegment(
        previous: FollowMeHistorySample,
        current: FollowMeHistorySample,
    ): Boolean =
        current.encounterCount != previous.encounterCount ||
            current.timestamp - previous.timestamp > ENCOUNTER_SEGMENT_GAP_MS

    private fun hasUsableLocation(sample: SignalSample): Boolean {
        val latitude = sample.latitude
        val longitude = sample.longitude
        val accuracy = sample.locationAccuracy
        return latitude != null &&
            longitude != null &&
            accuracy != null &&
            latitude.isFinite() &&
            longitude.isFinite() &&
            accuracy.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE &&
            accuracy > MIN_LOCATION_ACCURACY_METERS &&
            accuracy <= MAX_LOCATION_ACCURACY_METERS
    }

    private fun bucketStart(timestamp: Long): Long =
        Math.floorDiv(timestamp, SIGNAL_BUCKET_MS) * SIGNAL_BUCKET_MS

    private data class AnalysisSignalIdentity(
        val timestamp: Long,
        val rssi: Int,
        val observedMac: String?,
        val latitude: Double?,
        val longitude: Double?,
        val locationAccuracy: Float?,
    )

    private const val SIGNAL_BUCKET_MS = 30_000L
    private const val ENCOUNTER_SEGMENT_GAP_MS = 120_000L
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_LOCATION_ACCURACY_METERS = 0f
    private const val MAX_LOCATION_ACCURACY_METERS = 100f
}

private fun hasCoordinates(sample: SignalSample): Boolean =
    sample.latitude != null && sample.longitude != null

private fun List<SignalSample>.averageRssi(): Double? =
    if (isEmpty()) {
        null
    } else {
        sumOf { it.rssi.toLong() }.toDouble() / size
    }
