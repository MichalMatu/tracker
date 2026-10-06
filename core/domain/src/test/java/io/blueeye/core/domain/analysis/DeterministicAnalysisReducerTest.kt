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
import io.blueeye.core.model.analysis.AnalysisIdentityDisposition
import io.blueeye.core.model.analysis.AnalysisInput
import io.blueeye.core.model.analysis.AnalysisQualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicAnalysisReducerTest {
    @Test
    fun `reduction is invariant to input permutation`() {
        val signals =
            listOf(
                signal(timestamp = 61_000L, rssi = -60, latitude = 51.1, longitude = 17.0, accuracy = 12f),
                signal(timestamp = 1_000L, rssi = -70, latitude = 51.2, longitude = 17.1, accuracy = 20f),
            )
        val history =
            listOf(
                followMe(timestamp = 70_000L, status = TrackingStatus.SUSPICIOUS, moved = true),
                followMe(timestamp = 10_000L, status = TrackingStatus.SAFE, moved = false),
            )
        val events =
            listOf(
                alertEvent(timestamp = 80_000L, confidence = DetectionConfidence.HIGH),
                alertEvent(timestamp = 20_000L, confidence = DetectionConfidence.MEDIUM),
            )
        val candidates =
            listOf(
                identityCandidate(timestamp = 90_000L, candidateFingerprint = "candidate-b", confidence = 0.8f),
                identityCandidate(timestamp = 30_000L, candidateFingerprint = "candidate-a", confidence = 0.7f),
            )

        val forward =
            DeterministicAnalysisReducer.reduce(
                input(
                    signals = signals,
                    history = history,
                    events = events,
                    candidates = candidates,
                ),
            )
        val reversed =
            DeterministicAnalysisReducer.reduce(
                input(
                    signals = signals.reversed(),
                    history = history.reversed(),
                    events = events.reversed(),
                    candidates = candidates.reversed(),
                ),
            )

        assertEquals(forward, reversed)
    }

    @Test
    fun `exact duplicate samples are reduced and invalid RSSI is rejected`() {
        val valid = signal(timestamp = 1_000L, rssi = -65)
        val invalid = signal(timestamp = 2_000L, rssi = 127)

        val result =
            DeterministicAnalysisReducer.reduce(
                input(signals = listOf(valid, valid, invalid)),
            )

        assertEquals(3, result.signal.sourceSampleCount)
        assertEquals(2, result.signal.uniqueSampleCount)
        assertEquals(1, result.signal.acceptedSampleCount)
        assertEquals(1, result.signal.duplicateSampleCount)
        assertEquals(1, result.signal.rejectedSampleCount)
        assertTrue(AnalysisQualityFlag.DUPLICATES_REDUCED in result.qualityFlags)
        assertTrue(AnalysisQualityFlag.INVALID_SIGNAL_SAMPLES in result.qualityFlags)
    }

    @Test
    fun `fixed time buckets split exactly at minute boundary`() {
        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    device = device(firstSeenAt = 0L, lastSeenAt = 60_000L),
                    signals =
                        listOf(
                            signal(timestamp = 59_999L, rssi = -70),
                            signal(timestamp = 60_000L, rssi = -60),
                        ),
                ),
            )

        assertEquals(listOf(0L, 60_000L), result.timeBuckets.map { it.startTimestamp })
        assertEquals(listOf(60_000L, 120_000L), result.timeBuckets.map { it.endExclusiveTimestamp })
        assertEquals(listOf(1, 1), result.timeBuckets.map { it.sampleCount })
    }

    @Test
    fun `coexisting identity candidate is retained as conflict instead of continuity`() {
        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    signals =
                        listOf(
                            signal(timestamp = 10_000L, rssi = -55, fingerprint = PRIMARY),
                            signal(timestamp = 11_500L, rssi = -57, fingerprint = CANDIDATE),
                        ),
                    candidates =
                        listOf(
                            identityCandidate(
                                timestamp = 12_000L,
                                candidateFingerprint = CANDIDATE,
                                confidence = 0.9f,
                            ),
                        ),
                ),
            )

        assertEquals(
            AnalysisIdentityDisposition.COEXISTENCE_CONFLICT,
            result.identity.candidates.single().disposition,
        )
        assertTrue(AnalysisQualityFlag.IDENTITY_COEXISTENCE_DETECTED in result.qualityFlags)
        assertTrue(
            result.contradictions.any { it.type == AnalysisContradictionType.IDENTITY_COEXISTENCE },
        )
    }

    @Test
    fun `RSSI summary is stable and includes median`() {
        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    signals =
                        listOf(
                            signal(timestamp = 1L, rssi = -80),
                            signal(timestamp = 2L, rssi = -60),
                            signal(timestamp = 3L, rssi = -70),
                            signal(timestamp = 4L, rssi = -50),
                        ),
                ),
            )

        assertEquals(-80, result.signal.minRssi)
        assertEquals(-50, result.signal.maxRssi)
        assertEquals(-65.0, result.signal.averageRssi)
        assertEquals(-65.0, result.signal.medianRssi)
    }

    @Test
    fun `poor or absent GPS is summarized without coordinates`() {
        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    signals =
                        listOf(
                            signal(timestamp = 1L, rssi = -70),
                            signal(
                                timestamp = 2L,
                                rssi = -71,
                                latitude = 51.1,
                                longitude = 17.0,
                                accuracy = 150f,
                            ),
                        ),
                ),
            )

        assertEquals(1, result.locationQuality.sourceLocationCount)
        assertEquals(0, result.locationQuality.usableLocationCount)
        assertEquals(1, result.locationQuality.rejectedLocationCount)
        assertEquals(null, result.locationQuality.bestAccuracyMeters)
        assertTrue(AnalysisQualityFlag.NO_USABLE_LOCATION in result.qualityFlags)
        assertTrue(AnalysisQualityFlag.INVALID_LOCATION_SAMPLES in result.qualityFlags)
    }

    @Test
    fun `Follow-Me observations are segmented by gaps and movement state`() {
        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    history =
                        listOf(
                            followMe(timestamp = 0L, status = TrackingStatus.SAFE, moved = true, encounter = 1),
                            followMe(timestamp = 10_000L, status = TrackingStatus.SAFE, moved = true, encounter = 1),
                            followMe(timestamp = 20_000L, status = TrackingStatus.SAFE, moved = false, encounter = 1),
                            followMe(timestamp = 25_000L, status = TrackingStatus.SAFE, moved = true, encounter = 1),
                            followMe(timestamp = 70_000L, status = TrackingStatus.SAFE, moved = true, encounter = 2),
                        ),
                ),
            )

        assertEquals(2, result.movement.encounterSegmentCount)
        assertEquals(3, result.movement.movingSegmentCount)
        assertEquals(4, result.movement.movingObservationCount)
        assertEquals(1, result.movement.stationaryObservationCount)
        assertEquals(2, result.movement.maxEncounterCount)
    }

    @Test
    fun `representative evidence is bounded and prioritizes confidence`() {
        val deviceEvidence =
            (0 until 9).map { index ->
                evidence(
                    timestamp = index.toLong(),
                    confidence = if (index == 8) DetectionConfidence.HIGH else DetectionConfidence.LOW,
                    reason = "device-evidence-$index",
                )
            }
        val event =
            alertEvent(
                timestamp = 100L,
                confidence = DetectionConfidence.CRITICAL,
                reason = "critical-alert",
            )

        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    device = device(evidence = deviceEvidence),
                    events = listOf(event),
                ),
            )

        assertEquals(8, result.representativeEvidence.size)
        assertEquals(DetectionConfidence.CRITICAL, result.representativeEvidence.first().confidence)
        assertEquals(AlertEvidenceEventType.FOLLOW_ME_ALERT, result.representativeEvidence.first().alertEventType)
        assertTrue(AnalysisQualityFlag.REPRESENTATIVE_EVIDENCE_TRUNCATED in result.qualityFlags)
    }

    @Test
    fun `contradictions expose review status and movement conflicts`() {
        val result =
            DeterministicAnalysisReducer.reduce(
                input(
                    device = device(trackingStatus = TrackingStatus.SAFE),
                    history =
                        listOf(
                            followMe(
                                timestamp = 50_000L,
                                status = TrackingStatus.DANGEROUS,
                                moved = false,
                            ),
                        ),
                    candidates =
                        listOf(
                            identityCandidate(
                                timestamp = 60_000L,
                                candidateFingerprint = CANDIDATE,
                                confidence = 0.95f,
                                verdict = IdentityCarryoverVerdict.FALSE_MATCH,
                            ),
                        ),
                ),
            )

        val types = result.contradictions.map { it.type }.toSet()
        assertTrue(AnalysisContradictionType.IDENTITY_REVIEW_CONFLICT in types)
        assertTrue(AnalysisContradictionType.TRACKING_STATUS_DIVERGENCE in types)
        assertTrue(AnalysisContradictionType.FOLLOW_ME_WITHOUT_MOVEMENT in types)
    }

    private fun input(
        device: Device = device(),
        signals: List<SignalSample> = emptyList(),
        history: List<FollowMeHistorySample> = emptyList(),
        events: List<AlertEvidenceEvent> = emptyList(),
        candidates: List<IdentityContinuityCandidate> = emptyList(),
    ): AnalysisInput =
        AnalysisInput(
            device = device,
            signalSamples = signals,
            followMeHistory = history,
            alertEvidenceEvents = events,
            identityCandidates = candidates,
        )

    private fun device(
        trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        firstSeenAt: Long = 0L,
        lastSeenAt: Long = 100_000L,
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = PRIMARY,
            macAddress = "AA:BB:CC:DD:EE:FF",
            macAddressType = MacAddressType.RANDOM,
            technology = "BLE",
            name = "Test device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = trackingStatus,
            followingScore = 0.5f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = true,
            alertVibration = true,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            encounterCount = 1,
            evidence = evidence,
        )

    private fun signal(
        timestamp: Long,
        rssi: Int,
        fingerprint: String = PRIMARY,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null,
    ): SignalSample =
        SignalSample(
            timestamp = timestamp,
            rssi = rssi,
            deviceFingerprint = fingerprint,
            observedMac = fingerprint,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = accuracy,
        )

    private fun followMe(
        timestamp: Long,
        status: TrackingStatus,
        moved: Boolean?,
        encounter: Int = 1,
    ): FollowMeHistorySample =
        FollowMeHistorySample(
            timestamp = timestamp,
            observedMac = PRIMARY,
            trackingStatus = status,
            score = 0.5f,
            explanation = null,
            rssi = -65,
            encounterCount = encounter,
            durationScore = 1,
            rssiStabilityScore = 1,
            deviceTypeScore = 1,
            macBehaviorScore = 1,
            encounterScore = 1,
            userMoved = moved,
            baselineDevice = false,
        )

    private fun identityCandidate(
        timestamp: Long,
        candidateFingerprint: String,
        confidence: Float,
        verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
    ): IdentityContinuityCandidate =
        IdentityContinuityCandidate(
            id = timestamp,
            deviceFingerprint = PRIMARY,
            candidateFingerprint = candidateFingerprint,
            timestamp = timestamp,
            reasonCode = "WEIGHTED_FEATURE_MATCH",
            confidence = confidence,
            featureSummary = "stable-features",
            verdict = verdict,
        )

    private fun alertEvent(
        timestamp: Long,
        confidence: DetectionConfidence,
        reason: String = "alert-evidence",
    ): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = timestamp,
            deviceFingerprint = PRIMARY,
            observedMac = PRIMARY,
            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
            evidence = evidence(timestamp = timestamp, confidence = confidence, reason = reason),
        )

    private fun evidence(
        timestamp: Long,
        confidence: DetectionConfidence,
        reason: String,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.FOLLOW_ME_SCORE,
            confidence = confidence,
            reasonText = reason,
            timestamp = timestamp,
            rawValue = "raw-private-value",
            parsedValue = "reduced-value",
            isPassive = true,
            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
        )

    private companion object {
        private const val PRIMARY = "device-primary"
        private const val CANDIDATE = "device-candidate"
    }
}
