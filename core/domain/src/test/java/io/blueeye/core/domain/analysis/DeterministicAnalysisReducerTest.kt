package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisContradictionType
import io.blueeye.core.model.analysis.AnalysisMovementState
import io.blueeye.core.model.analysis.AnalysisQualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicAnalysisReducerTest {
    @Test
    fun `reduction is invariant to input permutation`() {
        val evidence =
            listOf(
                evidence(DetectionConfidence.HIGH, timestamp = NOW + 1_000L, reason = "High signal"),
                evidence(DetectionConfidence.MEDIUM, timestamp = NOW, reason = "Medium signal"),
            )
        val device = device(evidence = evidence)
        val signals =
            listOf(
                signal(NOW + 61_000L, -72, location = TestLocation(51.1, 17.0, 20f)),
                signal(NOW, -55),
                signal(NOW + 20_000L, -65),
            )
        val followMe =
            listOf(
                followMe(NOW + 10_000L, userMoved = true, score = 0.7f),
                followMe(NOW + 40_000L, userMoved = false, score = 0.4f),
            )
        val alerts =
            listOf(
                alert(NOW + 5_000L, DetectionConfidence.CRITICAL),
                alert(NOW + 3_000L, DetectionConfidence.MEDIUM),
            )
        val candidates =
            listOf(
                candidate(NOW + 2_000L, id = 2L, confidence = 0.8f),
                candidate(NOW + 1_000L, id = 1L, confidence = 0.7f),
            )

        val first =
            DeterministicAnalysisReducer.reduce(
                device = device,
                signalSamples = signals,
                followMeHistory = followMe,
                alertEvidenceEvents = alerts,
                identityCandidates = candidates,
            )
        val second =
            DeterministicAnalysisReducer.reduce(
                device = device.copy(evidence = evidence.reversed()),
                signalSamples = signals.reversed(),
                followMeHistory = followMe.reversed(),
                alertEvidenceEvents = alerts.reversed(),
                identityCandidates = candidates.reversed(),
            )

        assertEquals(first, second)
    }

    @Test
    fun `exact duplicate samples are reduced before summaries`() {
        val sample = signal(NOW, -60)

        val result =
            reduce(
                signalSamples = listOf(sample, sample),
            )

        assertEquals(2, result.signal.sourceSampleCount)
        assertEquals(1, result.signal.reducedSampleCount)
        assertEquals(1, result.signal.duplicateSampleCount)
        assertEquals(1, result.timeBuckets.single().sampleCount)
        assertTrue(AnalysisQualityFlag.DUPLICATE_SIGNAL_SAMPLES_REDUCED in result.qualityFlags)
    }

    @Test
    fun `fixed time buckets split exactly on minute boundary`() {
        val result =
            reduce(
                signalSamples =
                    listOf(
                        signal(59_999L, -60),
                        signal(60_000L, -70),
                    ),
            )

        assertEquals(listOf(0L, 60_000L), result.timeBuckets.map { it.startTimestamp })
        assertEquals(listOf(60_000L, 120_000L), result.timeBuckets.map { it.endTimestampExclusive })
    }

    @Test
    fun `identity candidate reports coexistence inside guard window only`() {
        val coexistence =
            reduce(
                signalSamples =
                    listOf(
                        signal(NOW, -60, fingerprint = FINGERPRINT),
                        signal(NOW + 1_999L, -61, fingerprint = OTHER_FINGERPRINT),
                    ),
                identityCandidates = listOf(candidate(NOW + 5_000L)),
            )
        val sequential =
            reduce(
                signalSamples =
                    listOf(
                        signal(NOW, -60, fingerprint = FINGERPRINT),
                        signal(NOW + 2_000L, -61, fingerprint = OTHER_FINGERPRINT),
                    ),
                identityCandidates = listOf(candidate(NOW + 5_000L)),
            )

        assertTrue(coexistence.identityCandidates.single().hasCoexistenceEvidence)
        assertFalse(sequential.identityCandidates.single().hasCoexistenceEvidence)
        assertTrue(
            coexistence.contradictions.any {
                it.type == AnalysisContradictionType.IDENTITY_COEXISTENCE
            },
        )
    }

    @Test
    fun `rssi summary is deterministic and keeps distribution basics`() {
        val result =
            reduce(
                signalSamples =
                    listOf(
                        signal(NOW + 2L, -80),
                        signal(NOW, -60),
                        signal(NOW + 1L, -70),
                        signal(NOW + 3L, -50),
                    ),
            )

        val rssi = requireNotNull(result.signal.rssi)
        assertEquals(4, rssi.sampleCount)
        assertEquals(-80, rssi.minimum)
        assertEquals(-50, rssi.maximum)
        assertEquals(-65.0, rssi.average, 0.0)
        assertEquals(-65.0, rssi.median, 0.0)
    }

    @Test
    fun `location summary distinguishes missing and rejected gps without exposing coordinates`() {
        val result =
            reduce(
                signalSamples =
                    listOf(
                        signal(NOW, -60),
                        signal(
                            NOW + 1L,
                            -61,
                            location = TestLocation(51.1, 17.0, 150f),
                        ),
                        signal(
                            NOW + 2L,
                            -62,
                            location = TestLocation(91.0, 17.0, 10f),
                        ),
                    ),
            )

        assertEquals(3, result.locationQuality.totalSampleCount)
        assertEquals(0, result.locationQuality.usableSampleCount)
        assertEquals(2, result.locationQuality.rejectedSampleCount)
        assertEquals(1, result.locationQuality.missingSampleCount)
        assertTrue(AnalysisQualityFlag.NO_USABLE_LOCATION in result.qualityFlags)
        assertTrue(AnalysisQualityFlag.REJECTED_LOCATION_SAMPLES in result.qualityFlags)
        assertFalse(AnalysisQualityFlag.NO_LOCATION_DATA in result.qualityFlags)
    }

    @Test
    fun `follow me history segments on movement changes and long gaps`() {
        val result =
            reduce(
                followMeHistory =
                    listOf(
                        followMe(NOW, userMoved = true),
                        followMe(NOW + 10_000L, userMoved = true),
                        followMe(NOW + 40_001L, userMoved = true),
                        followMe(NOW + 45_000L, userMoved = false),
                        followMe(NOW + 50_000L, userMoved = null),
                    ),
            )

        assertEquals(4, result.movement.segments.size)
        assertEquals(
            listOf(
                AnalysisMovementState.MOVING,
                AnalysisMovementState.MOVING,
                AnalysisMovementState.STATIONARY,
                AnalysisMovementState.UNKNOWN,
            ),
            result.movement.segments.map { it.state },
        )
        assertEquals(listOf(2, 1, 1, 1), result.movement.segments.map { it.sampleCount })
    }

    @Test
    fun `representative evidence is bounded and prioritizes confidence then recency`() {
        val lowEvidence =
            (0 until 10).map { index ->
                evidence(
                    confidence = DetectionConfidence.LOW,
                    timestamp = NOW + index,
                    reason = "low-$index",
                )
            }
        val critical =
            evidence(
                confidence = DetectionConfidence.CRITICAL,
                timestamp = NOW - 100L,
                reason = "critical",
            )

        val result =
            reduce(
                device = device(evidence = lowEvidence),
                alertEvidenceEvents =
                    listOf(
                        AlertEvidenceEvent(
                            timestamp = critical.timestamp,
                            deviceFingerprint = FINGERPRINT,
                            observedMac = "AA:BB:CC:DD:EE:FF",
                            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                            evidence = critical,
                        ),
                    ),
            )

        assertEquals(8, result.representativeEvidence.size)
        assertEquals(DetectionConfidence.CRITICAL, result.representativeEvidence.first().confidence)
        assertEquals("critical", result.representativeEvidence.first().reasonText)
    }

    @Test
    fun `conflicting reviewed identity verdicts are surfaced as contradiction`() {
        val result =
            reduce(
                identityCandidates =
                    listOf(
                        candidate(
                            timestamp = NOW,
                            id = 1L,
                            verdict = IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
                        ),
                        candidate(
                            timestamp = NOW + 1L,
                            id = 2L,
                            verdict = IdentityCarryoverVerdict.FALSE_MATCH,
                        ),
                    ),
            )

        val summary = result.identityCandidates.single()
        assertEquals(2, summary.observationCount)
        assertEquals(IdentityCarryoverVerdict.FALSE_MATCH, summary.latestVerdict)
        assertTrue(
            result.contradictions.any {
                it.type == AnalysisContradictionType.IDENTITY_VERDICT_CONFLICT
            },
        )
    }

    private fun reduce(
        device: Device = device(),
        signalSamples: List<SignalSample> = emptyList(),
        followMeHistory: List<FollowMeHistorySample> = emptyList(),
        alertEvidenceEvents: List<AlertEvidenceEvent> = emptyList(),
        identityCandidates: List<IdentityContinuityCandidate> = emptyList(),
    ) = DeterministicAnalysisReducer.reduce(
        device = device,
        signalSamples = signalSamples,
        followMeHistory = followMeHistory,
        alertEvidenceEvents = alertEvidenceEvents,
        identityCandidates = identityCandidates,
    )

    private fun device(evidence: List<DetectionEvidence> = emptyList()): Device =
        Device(
            fingerprint = FINGERPRINT,
            macAddress = "AA:BB:CC:DD:EE:FF",
            macAddressType = MacAddressType.RANDOM,
            technology = "BLE",
            name = "Test device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0.25f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -60,
            encounterCount = 1,
            evidence = evidence,
        )

    private fun signal(
        timestamp: Long,
        rssi: Int,
        fingerprint: String = FINGERPRINT,
        location: TestLocation? = null,
    ): SignalSample =
        SignalSample(
            timestamp = timestamp,
            rssi = rssi,
            deviceFingerprint = fingerprint,
            observedMac = "AA:BB:CC:DD:EE:FF",
            latitude = location?.latitude,
            longitude = location?.longitude,
            locationAccuracy = location?.accuracyMeters,
        )

    private fun followMe(
        timestamp: Long,
        userMoved: Boolean?,
        score: Float = 0.5f,
    ): FollowMeHistorySample =
        FollowMeHistorySample(
            timestamp = timestamp,
            observedMac = "AA:BB:CC:DD:EE:FF",
            trackingStatus = TrackingStatus.SUSPICIOUS,
            score = score,
            explanation = null,
            rssi = -60,
            encounterCount = 2,
            durationScore = 1,
            rssiStabilityScore = 1,
            deviceTypeScore = 1,
            macBehaviorScore = 1,
            encounterScore = 1,
            userMoved = userMoved,
            baselineDevice = false,
        )

    private fun candidate(
        timestamp: Long,
        id: Long = 1L,
        confidence: Float = 0.8f,
        verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
    ): IdentityContinuityCandidate =
        IdentityContinuityCandidate(
            id = id,
            deviceFingerprint = FINGERPRINT,
            candidateFingerprint = OTHER_FINGERPRINT,
            timestamp = timestamp,
            reasonCode = "WEIGHTED_FEATURE_MATCH",
            confidence = confidence,
            featureSummary = "bounded test features",
            verdict = verdict,
        )

    private fun evidence(
        confidence: DetectionConfidence,
        timestamp: Long,
        reason: String,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.FOLLOW_ME_SCORE,
            confidence = confidence,
            reasonText = reason,
            timestamp = timestamp,
            rawValue = "raw-private-value",
            parsedValue = "parsed-private-value",
            isPassive = true,
            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
        )

    private fun alert(
        timestamp: Long,
        confidence: DetectionConfidence,
    ): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = timestamp,
            deviceFingerprint = FINGERPRINT,
            observedMac = "AA:BB:CC:DD:EE:FF",
            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
            evidence =
                evidence(
                    confidence = confidence,
                    timestamp = timestamp,
                    reason = "alert-$timestamp",
                ),
        )

    private data class TestLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    )

    private companion object {
        private const val NOW = 1_790_000_000_000L
        private const val FINGERPRINT = "device-a"
        private const val OTHER_FINGERPRINT = "device-b"
    }
}
