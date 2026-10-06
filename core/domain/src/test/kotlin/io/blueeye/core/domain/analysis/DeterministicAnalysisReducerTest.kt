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
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisContradictionV1
import io.blueeye.core.model.analysis.AnalysisQualityFlagV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicAnalysisReducerTest {
    @Test
    fun `result is invariant to input ordering`() {
        val device =
            device(
                evidence =
                    listOf(
                        evidence(timestamp = 10L, confidence = DetectionConfidence.HIGH),
                        evidence(timestamp = 20L, confidence = DetectionConfidence.MEDIUM),
                    ),
            )
        val signals =
            listOf(
                signal(timestamp = 60_000L, rssi = -50),
                signal(timestamp = 30_000L, rssi = -70),
                signal(timestamp = 45_000L, rssi = -60),
            )
        val followMe =
            listOf(
                followMe(timestamp = 20L, score = 70f),
                followMe(timestamp = 10L, score = 30f),
            )
        val alerts =
            listOf(
                alert(timestamp = 30L),
                alert(timestamp = 15L),
            )
        val identities =
            listOf(
                identity(id = 2L, timestamp = 25L, candidateFingerprint = "candidate-b"),
                identity(id = 1L, timestamp = 5L, candidateFingerprint = "candidate-a"),
            )

        val forward =
            DeterministicAnalysisReducer.reduce(
                AnalysisReducerInputV1(
                    device = device,
                    signalSamples = signals,
                    followMeHistory = followMe,
                    alertEvidenceEvents = alerts,
                    identityCandidates = identities,
                ),
            )
        val reversed =
            DeterministicAnalysisReducer.reduce(
                AnalysisReducerInputV1(
                    device = device,
                    signalSamples = signals.reversed(),
                    followMeHistory = followMe.reversed(),
                    alertEvidenceEvents = alerts.reversed(),
                    identityCandidates = identities.reversed(),
                ),
            )

        assertEquals(forward, reversed)
    }

    @Test
    fun `exact duplicate samples collapse and thirty second boundaries remain distinct`() {
        val first = signal(timestamp = 29_999L, rssi = -70)
        val second = signal(timestamp = 30_000L, rssi = -50)

        val candidate =
            reduce(
                signalSamples = listOf(first, second, first),
                completeness = completeHistory(),
            )

        assertEquals(2, candidate.signal.sampleCount)
        assertEquals(2, candidate.signal.buckets.size)
        assertEquals(0L, candidate.signal.buckets[0].startedAt)
        assertEquals(AnalysisReducerRulesV1.signalBucketMs, candidate.signal.buckets[1].startedAt)
        assertEquals(-70, candidate.signal.minRssi)
        assertEquals(-50, candidate.signal.maxRssi)
        assertEquals(-60.0, candidate.signal.averageRssi ?: 0.0, DOUBLE_DELTA)
    }

    @Test
    fun `location quality reports usable poor and missing observations without coordinates in output`() {
        val candidate =
            reduce(
                signalSamples =
                    listOf(
                        signal(
                            timestamp = 1L,
                            latitude = 51.1079,
                            longitude = 17.0385,
                            accuracy = 8f,
                        ),
                        signal(
                            timestamp = 2L,
                            latitude = 51.1080,
                            longitude = 17.0386,
                            accuracy = 150f,
                        ),
                        signal(timestamp = 3L),
                    ),
                completeness = completeHistory(),
            )

        val quality = candidate.signal.locationQuality
        assertEquals(2, quality.samplesWithCoordinates)
        assertEquals(1, quality.usableLocationSamples)
        assertEquals(1, quality.poorLocationSamples)
        assertEquals(8f, quality.bestAccuracyMeters)
        assertEquals(8f, quality.worstAccuracyMeters)
        assertTrue(candidate.diagnostics.qualityFlags.contains(AnalysisQualityFlagV1.POOR_LOCATION_QUALITY))
        assertFalse(candidate.diagnostics.qualityFlags.contains(AnalysisQualityFlagV1.NO_LOCATION_DATA))
    }

    @Test
    fun `identity relations preserve both sides and rejected relation becomes contradiction`() {
        val candidate =
            reduce(
                identityCandidates =
                    listOf(
                        identity(
                            id = 2L,
                            timestamp = 20L,
                            deviceFingerprint = "other-device",
                            candidateFingerprint = FINGERPRINT,
                            verdict = IdentityCarryoverVerdict.FALSE_MATCH,
                        ),
                        identity(
                            id = 1L,
                            timestamp = 10L,
                            deviceFingerprint = FINGERPRINT,
                            candidateFingerprint = "candidate-a",
                        ),
                    ),
                completeness = completeHistory(),
            )

        assertEquals(
            listOf("candidate-a", "other-device"),
            candidate.identity.relations.map { it.relatedLocalCandidateKey },
        )
        assertTrue(
            candidate.diagnostics.contradictions.contains(
                AnalysisContradictionV1.REJECTED_IDENTITY_RELATION_PRESENT,
            ),
        )
    }

    @Test
    fun `representative evidence is bounded deterministic and prefers durable event metadata`() {
        val duplicatedEvidence =
            evidence(
                timestamp = 100L,
                confidence = DetectionConfidence.CRITICAL,
                reason = "critical duplicate",
            )
        val additionalEvidence =
            (1L..10L).map { index ->
                evidence(
                    timestamp = index,
                    confidence = DetectionConfidence.HIGH,
                    reason = "evidence-$index",
                )
            }
        val device = device(evidence = additionalEvidence + duplicatedEvidence)
        val alert =
            AlertEvidenceEvent(
                timestamp = duplicatedEvidence.timestamp,
                deviceFingerprint = FINGERPRINT,
                observedMac = OBSERVED_MAC,
                eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                evidence = duplicatedEvidence,
            )

        val candidate =
            DeterministicAnalysisReducer.reduce(
                AnalysisReducerInputV1(
                    device = device,
                    signalSamples = emptyList(),
                    followMeHistory = emptyList(),
                    alertEvidenceEvents = listOf(alert),
                    identityCandidates = emptyList(),
                    historyCompleteness = completeHistory(),
                ),
            )

        val representative = candidate.diagnostics.representativeEvidence
        assertEquals(8, representative.size)
        assertEquals("critical duplicate", representative.first().reasonText)
        assertEquals(AlertEvidenceEventType.FOLLOW_ME_ALERT, representative.first().eventType)
        assertEquals(
            representative,
            representative.sortedWith(
                compareByDescending<io.blueeye.core.model.analysis.AnalysisEvidenceSummaryV1> {
                    it.confidence.ordinal
                }.thenByDescending { it.timestamp },
            ),
        )
    }

    @Test
    fun `incomplete histories and conservative contradictions remain explicit`() {
        val candidate =
            reduce(
                device =
                    device(
                        trackingStatus = TrackingStatus.SUSPICIOUS,
                        calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                    ),
                followMeHistory =
                    listOf(
                        followMe(
                            timestamp = 1L,
                            score = 90f,
                            status = TrackingStatus.DANGEROUS,
                            baseline = true,
                        ),
                    ),
            )

        assertEquals(
            listOf(
                AnalysisQualityFlagV1.FOLLOW_ME_HISTORY_INCOMPLETE,
                AnalysisQualityFlagV1.IDENTITY_HISTORY_INCOMPLETE,
                AnalysisQualityFlagV1.NO_LOCATION_DATA,
                AnalysisQualityFlagV1.NO_SIGNAL_SAMPLES,
                AnalysisQualityFlagV1.SIGNAL_HISTORY_INCOMPLETE,
            ),
            candidate.diagnostics.qualityFlags,
        )
        assertTrue(
            candidate.diagnostics.contradictions.contains(
                AnalysisContradictionV1.CALIBRATED_SAFE_BUT_LOCAL_ATTENTION_ACTIVE,
            ),
        )
        assertTrue(
            candidate.diagnostics.contradictions.contains(
                AnalysisContradictionV1.BASELINE_OBSERVATION_WITH_DANGEROUS_STATUS,
            ),
        )
    }

    @Test
    fun `large timelines remain bounded and report truncation`() {
        val signalCount = AnalysisReducerRulesV1.maxSignalBuckets + 2
        val identityCount = AnalysisReducerRulesV1.maxIdentityRelations + 2
        val candidate =
            reduce(
                signalSamples =
                    (0 until signalCount).map { index ->
                        signal(
                            timestamp = index.toLong() * AnalysisReducerRulesV1.signalBucketMs,
                        )
                    },
                identityCandidates =
                    (0 until identityCount).map { index ->
                        identity(
                            id = index.toLong(),
                            timestamp = index.toLong(),
                            candidateFingerprint = "candidate-$index",
                        )
                    },
                completeness = completeHistory(),
            )

        assertEquals(signalCount, candidate.signal.totalBucketCount)
        assertEquals(AnalysisReducerRulesV1.maxSignalBuckets, candidate.signal.buckets.size)
        assertEquals(identityCount, candidate.identity.totalRelationCount)
        assertEquals(AnalysisReducerRulesV1.maxIdentityRelations, candidate.identity.relations.size)
        assertTrue(
            candidate.diagnostics.qualityFlags.contains(
                AnalysisQualityFlagV1.SIGNAL_BUCKETS_TRUNCATED,
            ),
        )
        assertTrue(
            candidate.diagnostics.qualityFlags.contains(
                AnalysisQualityFlagV1.IDENTITY_RELATIONS_TRUNCATED,
            ),
        )
    }

    @Test
    fun `foreign and unscoped signal samples are excluded`() {
        val candidate =
            reduce(
                signalSamples =
                    listOf(
                        signal(timestamp = 1L, fingerprint = FINGERPRINT),
                        signal(timestamp = 2L, fingerprint = ""),
                        signal(timestamp = 3L, fingerprint = "different-device"),
                    ),
                completeness = completeHistory(),
            )

        assertEquals(1, candidate.signal.sampleCount)
        assertTrue(
            candidate.diagnostics.qualityFlags.contains(
                AnalysisQualityFlagV1.OUT_OF_SCOPE_SIGNAL_SAMPLES_DROPPED,
            ),
        )
    }

    private fun reduce(
        device: Device = device(),
        signalSamples: List<SignalSample> = emptyList(),
        followMeHistory: List<FollowMeHistorySample> = emptyList(),
        alertEvidenceEvents: List<AlertEvidenceEvent> = emptyList(),
        identityCandidates: List<IdentityContinuityCandidate> = emptyList(),
        completeness: AnalysisHistoryCompleteness = AnalysisHistoryCompleteness(),
    ) =
        DeterministicAnalysisReducer.reduce(
            AnalysisReducerInputV1(
                device = device,
                signalSamples = signalSamples,
                followMeHistory = followMeHistory,
                alertEvidenceEvents = alertEvidenceEvents,
                identityCandidates = identityCandidates,
                historyCompleteness = completeness,
            ),
        )

    private fun device(
        trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = FINGERPRINT,
            macAddress = OBSERVED_MAC,
            macAddressType = MacAddressType.RANDOM,
            technology = "BLE",
            name = null,
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = trackingStatus,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            isTrackingEnabled = true,
            isIgnoredForTracking = false,
            firstSeenAt = 1L,
            lastSeenAt = 90_000L,
            rssi = -60,
            encounterCount = 1,
            calibrationLabel = calibrationLabel,
            evidence = evidence,
        )

    private fun signal(
        timestamp: Long,
        rssi: Int = -60,
        fingerprint: String = FINGERPRINT,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null,
    ): SignalSample =
        SignalSample(
            timestamp = timestamp,
            rssi = rssi,
            deviceFingerprint = fingerprint,
            observedMac = OBSERVED_MAC,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = accuracy,
        )

    private fun followMe(
        timestamp: Long,
        score: Float,
        status: TrackingStatus = TrackingStatus.SUSPICIOUS,
        baseline: Boolean = false,
    ): FollowMeHistorySample =
        FollowMeHistorySample(
            timestamp = timestamp,
            observedMac = OBSERVED_MAC,
            trackingStatus = status,
            score = score,
            explanation = null,
            rssi = -60,
            encounterCount = 2,
            durationScore = 1,
            rssiStabilityScore = 1,
            deviceTypeScore = 1,
            macBehaviorScore = 1,
            encounterScore = 1,
            userMoved = true,
            baselineDevice = baseline,
        )

    private fun alert(timestamp: Long): AlertEvidenceEvent {
        val evidence =
            evidence(
                timestamp = timestamp,
                confidence = DetectionConfidence.HIGH,
                reason = "alert-$timestamp",
            )
        return AlertEvidenceEvent(
            timestamp = timestamp,
            deviceFingerprint = FINGERPRINT,
            observedMac = OBSERVED_MAC,
            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
            evidence = evidence,
        )
    }

    private fun identity(
        id: Long,
        timestamp: Long,
        deviceFingerprint: String = FINGERPRINT,
        candidateFingerprint: String,
        verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
    ): IdentityContinuityCandidate =
        IdentityContinuityCandidate(
            id = id,
            deviceFingerprint = deviceFingerprint,
            candidateFingerprint = candidateFingerprint,
            timestamp = timestamp,
            reasonCode = "TEST_RELATION",
            confidence = 0.8f,
            featureSummary = "stable-feature",
            verdict = verdict,
        )

    private fun evidence(
        timestamp: Long,
        confidence: DetectionConfidence,
        reason: String = "evidence",
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

    private fun completeHistory() =
        AnalysisHistoryCompleteness(
            signalHistoryComplete = true,
            followMeHistoryComplete = true,
            identityHistoryComplete = true,
        )

    private companion object {
        const val FINGERPRINT = "device-1"
        const val OBSERVED_MAC = "AA:BB:CC:DD:EE:FF"
        const val DOUBLE_DELTA = 0.0001
    }
}
