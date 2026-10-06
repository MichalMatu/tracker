package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisContradictionV1
import io.blueeye.core.model.analysis.AnalysisHistoryCompletenessV1
import io.blueeye.core.model.analysis.AnalysisInputV1
import io.blueeye.core.model.analysis.AnalysisQualityFlagV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicAnalysisReducerTest {
    @Test
    fun `result is invariant to history input order`() {
        val signals =
            listOf(
                signal(timestamp = 1_000L, rssi = -60, accuracy = 8f),
                signal(timestamp = 31_000L, rssi = -72, accuracy = 20f),
            )
        val followMe =
            listOf(
                followMe(timestamp = 2_000L, encounterCount = 1, score = 20f),
                followMe(timestamp = 32_000L, encounterCount = 2, score = 70f),
            )
        val alerts =
            listOf(
                alert(timestamp = 35_000L, eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT),
                alert(timestamp = 5_000L, eventType = AlertEvidenceEventType.WATCHLIST_RETURN),
            )
        val identities =
            listOf(
                identity(timestamp = 20_000L, relatedKey = "related-b"),
                identity(timestamp = 10_000L, relatedKey = "related-a"),
            )
        val completeness = completeHistories()

        val forward =
            DeterministicAnalysisReducer.reduce(
                AnalysisInputV1(device(), signals, followMe, alerts, identities, completeness),
            )
        val reversed =
            DeterministicAnalysisReducer.reduce(
                AnalysisInputV1(
                    device = device(),
                    signalSamples = signals.reversed(),
                    followMeHistory = followMe.reversed(),
                    alertEvents = alerts.reversed(),
                    identityCandidates = identities.reversed(),
                    completeness = completeness,
                ),
            )

        assertEquals(forward, reversed)
    }

    @Test
    fun `signal reduction removes exact analysis duplicates and uses fixed buckets`() {
        val first = signal(timestamp = 1_000L, rssi = -60, accuracy = 10f)
        val summary =
            DeterministicAnalysisReducer.reduce(
                AnalysisInputV1(
                    device = device(firstSeenAt = 0L, lastSeenAt = 60_000L),
                    signalSamples =
                        listOf(
                            first,
                            first.copy(rawDataHex = "different-raw-payload"),
                            signal(timestamp = 29_999L, rssi = -70, accuracy = 150f),
                            signal(
                                timestamp = 30_000L,
                                rssi = -80,
                                latitude = null,
                                longitude = null,
                                accuracy = null,
                            ),
                        ),
                    followMeHistory = emptyList(),
                    alertEvents = emptyList(),
                    identityCandidates = emptyList(),
                    completeness = completeHistories(),
                ),
            ).observations.signal

        assertEquals(4, summary.sourceSampleCount)
        assertEquals(3, summary.uniqueSampleCount)
        assertEquals(listOf(0L, 30_000L), summary.buckets.map { it.bucketStartedAt })
        assertEquals(2, summary.buckets.first().sampleCount)
        assertEquals(-80, summary.rssi.minimum)
        assertEquals(-60, summary.rssi.maximum)
        assertEquals(-70.0, summary.rssi.average ?: 0.0, 0.0)
        assertEquals(2, summary.location.samplesWithCoordinates)
        assertEquals(1, summary.location.usableSamples)
        assertEquals(1, summary.location.poorSamples)
        assertEquals(10f, summary.location.bestUsableAccuracyMeters)
    }

    @Test
    fun `follow me history is segmented by encounter change and long gap`() {
        val candidate =
            DeterministicAnalysisReducer.reduce(
                AnalysisInputV1(
                    device = device(),
                    signalSamples = emptyList(),
                    followMeHistory =
                        listOf(
                            followMe(timestamp = 0L, encounterCount = 1, score = 10f, userMoved = false),
                            followMe(timestamp = 30_000L, encounterCount = 1, score = 20f, userMoved = true),
                            followMe(timestamp = 40_000L, encounterCount = 2, score = 60f, userMoved = true),
                            followMe(timestamp = 200_001L, encounterCount = 2, score = 70f, userMoved = true),
                        ),
                    alertEvents = emptyList(),
                    identityCandidates = emptyList(),
                    completeness = completeHistories(),
                ),
            )

        val movement = candidate.observations.movement
        assertEquals(3, movement.segments.size)
        assertEquals(2, movement.segments.first().sampleCount)
        assertTrue(movement.segments.first().userMovedObserved)
        assertEquals(70f, movement.maxScore)
        assertEquals(2, movement.maxEncounterCount)
    }

    @Test
    fun `representative evidence is bounded canonical and omits raw fields by contract`() {
        val evidence =
            (0 until 10).map { index ->
                evidence(
                    timestamp = index.toLong(),
                    confidence =
                        if (index == 9) {
                            DetectionConfidence.CRITICAL
                        } else {
                            DetectionConfidence.LOW
                        },
                    reason = "reason-$index",
                    raw = "private-$index",
                )
            }
        val candidate =
            DeterministicAnalysisReducer.reduce(
                AnalysisInputV1(
                    device = device(evidence = evidence),
                    signalSamples = emptyList(),
                    followMeHistory = emptyList(),
                    alertEvents = emptyList(),
                    identityCandidates = emptyList(),
                    completeness = completeHistories(),
                ),
            )

        val representatives = candidate.evidence.representativeEvidence
        assertEquals(8, representatives.size)
        assertEquals(DetectionConfidence.CRITICAL, representatives.first().confidence)
        assertEquals("reason-9", representatives.first().reasonText)
        assertFalse(representatives.any { it.reasonText.contains("private-") })
    }

    @Test
    fun `diagnostics expose counter evidence incompleteness and foreign input filtering`() {
        val candidate =
            DeterministicAnalysisReducer.reduce(
                AnalysisInputV1(
                    device =
                        device(
                            trackingStatus = TrackingStatus.DANGEROUS,
                            followingScore = 90f,
                            calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                            isSafeBeacon = true,
                        ),
                    signalSamples =
                        listOf(
                            signal(
                                timestamp = 1L,
                                rssi = -60,
                                accuracy = 10f,
                                deviceFingerprint = "foreign-device",
                            ),
                        ),
                    followMeHistory = emptyList(),
                    alertEvents =
                        listOf(
                            alert(
                                timestamp = 2L,
                                eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                                deviceFingerprint = "foreign-device",
                            ),
                        ),
                    identityCandidates =
                        listOf(
                            identity(
                                timestamp = 3L,
                                relatedKey = "related",
                                confidence = 0.9f,
                                verdict = IdentityCarryoverVerdict.FALSE_MATCH,
                            ),
                            identity(
                                timestamp = 4L,
                                relatedKey = "unrelated",
                                deviceFingerprint = "other-a",
                            ),
                        ),
                ),
            )

        assertTrue(
            AnalysisContradictionV1.USER_SUPPRESSION_CONFLICTS_WITH_LOCAL_ATTENTION in
                candidate.diagnostics.contradictions,
        )
        assertTrue(
            AnalysisContradictionV1.REVIEW_REJECTS_HIGH_CONFIDENCE_IDENTITY_RELATION in
                candidate.diagnostics.contradictions,
        )
        assertTrue(AnalysisQualityFlagV1.FOREIGN_DEVICE_INPUT_DROPPED in candidate.diagnostics.qualityFlags)
        assertTrue(AnalysisQualityFlagV1.NO_SIGNAL_SAMPLES in candidate.diagnostics.qualityFlags)
        assertTrue(AnalysisQualityFlagV1.SIGNAL_HISTORY_INCOMPLETE in candidate.diagnostics.qualityFlags)
        assertEquals(1, candidate.evidence.identityRelations.size)
    }

    private fun device(
        firstSeenAt: Long = 0L,
        lastSeenAt: Long = 300_000L,
        trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        followingScore: Float = 0f,
        calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
        isSafeBeacon: Boolean = false,
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = CANDIDATE_KEY,
            macAddress = "AA:BB:CC:DD:EE:FF",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Analysis fixture",
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = trackingStatus,
            followingScore = followingScore,
            isSafeBeacon = isSafeBeacon,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            encounterCount = 1,
            calibrationLabel = calibrationLabel,
            evidence = evidence,
        )

    private fun signal(
        timestamp: Long,
        rssi: Int,
        accuracy: Float?,
        latitude: Double? = 51.1,
        longitude: Double? = 17.0,
        deviceFingerprint: String = CANDIDATE_KEY,
    ): SignalSample =
        SignalSample(
            timestamp = timestamp,
            rssi = rssi,
            deviceFingerprint = deviceFingerprint,
            observedMac = "AA:BB:CC:DD:EE:FF",
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = accuracy,
        )

    private fun followMe(
        timestamp: Long,
        encounterCount: Int,
        score: Float,
        userMoved: Boolean? = null,
    ): FollowMeHistorySample =
        FollowMeHistorySample(
            timestamp = timestamp,
            observedMac = "AA:BB:CC:DD:EE:FF",
            trackingStatus =
                if (score >= ATTENTION_SCORE) {
                    TrackingStatus.SUSPICIOUS
                } else {
                    TrackingStatus.SAFE
                },
            score = score,
            explanation = "fixture",
            rssi = -60,
            encounterCount = encounterCount,
            durationScore = 0,
            rssiStabilityScore = 0,
            deviceTypeScore = 0,
            macBehaviorScore = 0,
            encounterScore = 0,
            userMoved = userMoved,
            baselineDevice = false,
        )

    private fun alert(
        timestamp: Long,
        eventType: AlertEvidenceEventType,
        deviceFingerprint: String = CANDIDATE_KEY,
    ): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = timestamp,
            deviceFingerprint = deviceFingerprint,
            observedMac = "AA:BB:CC:DD:EE:FF",
            eventType = eventType,
            evidence = evidence(timestamp = timestamp, reason = eventType.name),
        )

    private fun identity(
        timestamp: Long,
        relatedKey: String,
        confidence: Float = 0.5f,
        verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
        deviceFingerprint: String = CANDIDATE_KEY,
    ): IdentityContinuityCandidate =
        IdentityContinuityCandidate(
            id = timestamp,
            deviceFingerprint = deviceFingerprint,
            candidateFingerprint = relatedKey,
            timestamp = timestamp,
            reasonCode = "fixture",
            confidence = confidence,
            featureSummary = "fixture",
            verdict = verdict,
        )

    private fun evidence(
        timestamp: Long,
        confidence: DetectionConfidence = DetectionConfidence.MEDIUM,
        reason: String,
        raw: String? = null,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.FOLLOW_ME_SCORE,
            confidence = confidence,
            reasonText = reason,
            timestamp = timestamp,
            rawValue = raw,
            parsedValue = null,
            isPassive = true,
            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
        )

    private fun completeHistories(): AnalysisHistoryCompletenessV1 =
        AnalysisHistoryCompletenessV1(
            signalHistoryComplete = true,
            followMeHistoryComplete = true,
            alertHistoryComplete = true,
            identityHistoryComplete = true,
            deviceEvidenceHistoryComplete = true,
        )

    private companion object {
        const val CANDIDATE_KEY = "candidate"
        const val ATTENTION_SCORE = 51f
    }
}
