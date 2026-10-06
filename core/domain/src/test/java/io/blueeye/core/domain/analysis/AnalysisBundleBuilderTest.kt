package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisBundleJson
import io.blueeye.core.model.analysis.AnalysisCandidate
import io.blueeye.core.model.analysis.AnalysisContradiction
import io.blueeye.core.model.analysis.AnalysisContradictionType
import io.blueeye.core.model.analysis.AnalysisEncounterSummary
import io.blueeye.core.model.analysis.AnalysisEvidenceSummary
import io.blueeye.core.model.analysis.AnalysisIdentityCandidateSummary
import io.blueeye.core.model.analysis.AnalysisLocationQualitySummary
import io.blueeye.core.model.analysis.AnalysisMovementSegment
import io.blueeye.core.model.analysis.AnalysisMovementState
import io.blueeye.core.model.analysis.AnalysisMovementSummary
import io.blueeye.core.model.analysis.AnalysisQualityFlag
import io.blueeye.core.model.analysis.AnalysisRssiSummary
import io.blueeye.core.model.analysis.AnalysisSignalSummary
import io.blueeye.core.model.analysis.AnalysisTimeBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisBundleBuilderTest {
    @Test
    fun `bundle and json are deterministic across candidate permutation`() {
        val lowFingerprint = "AA:00:00:00:00:01"
        val highFingerprint = "FF:00:00:00:00:02"
        val relatedFingerprint = "11:22:33:44:55:66"
        val low =
            candidate(
                fingerprint = lowFingerprint,
                followingScore = 0.1f,
            )
        val high =
            candidate(
                fingerprint = highFingerprint,
                followingScore = 0.9f,
                relatedFingerprint = relatedFingerprint,
            )

        val first =
            AnalysisBundleBuilder.build(
                sessionId = SESSION_ID,
                startedAt = NOW,
                endedAt = NOW + 60_000L,
                candidates = listOf(high, low),
            )
        val second =
            AnalysisBundleBuilder.build(
                sessionId = SESSION_ID,
                startedAt = NOW,
                endedAt = NOW + 60_000L,
                candidates = listOf(low, high),
            )

        assertEquals(first, second)
        assertEquals(AnalysisBundleJson.encode(first), AnalysisBundleJson.encode(second))
        assertEquals(listOf("candidate-001", "candidate-002"), first.candidates.map { it.candidateId })
        assertEquals(0.1f, first.candidates.first().localVerdict.followingScore, 0f)
        assertEquals(
            "identity-001",
            first.candidates.last().identityCandidates.single().relatedCandidateId,
        )
        assertEquals(
            "identity-001",
            first.candidates.last().contradictions.single().relatedCandidateId,
        )
        assertEquals(first, AnalysisBundleJson.decode(AnalysisBundleJson.encode(first)))
    }

    @Test
    fun `bundle excludes private identifiers free form evidence and active probe evidence`() {
        val fingerprint = "AA:BB:CC:DD:EE:FF"
        val relatedFingerprint = "11:22:33:44:55:66"
        val passiveEvidence =
            evidence(
                source = EvidenceSource.NAME,
                reasonText = "Private evidence mentions AA:BB:CC:DD:EE:FF",
                isPassive = true,
                provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
            )
        val activeEvidence =
            evidence(
                source = EvidenceSource.GATT_PROBE,
                reasonText = "SECRET-GATT-PAYLOAD",
                isPassive = false,
                provenance = EvidenceProvenance.ACTIVE_GATT,
            )

        val bundle =
            AnalysisBundleBuilder.build(
                sessionId = SESSION_ID,
                startedAt = NOW,
                endedAt = NOW + 60_000L,
                candidates =
                    listOf(
                        candidate(
                            fingerprint = fingerprint,
                            followingScore = 0.5f,
                            relatedFingerprint = relatedFingerprint,
                            evidence = listOf(activeEvidence, passiveEvidence),
                        ),
                    ),
            )
        val json = AnalysisBundleJson.encode(bundle)
        val bundledCandidate = bundle.candidates.single()

        assertFalse(json.contains(fingerprint))
        assertFalse(json.contains(relatedFingerprint))
        assertFalse(json.contains(passiveEvidence.reasonText))
        assertFalse(json.contains(activeEvidence.reasonText))
        assertFalse(json.contains("GATT_PROBE"))
        assertFalse(json.contains("ACTIVE_GATT"))
        assertFalse(json.contains("reasonText"))
        assertFalse(json.contains("latitude"))
        assertFalse(json.contains("longitude"))
        assertEquals(1, bundledCandidate.omittedActiveEvidenceCount)
        assertEquals("NAME", bundledCandidate.representativeEvidence.single().source)
        assertFalse(bundle.privacy.exactCoordinatesIncluded)
        assertFalse(bundle.privacy.hardwareAddressesIncluded)
        assertFalse(bundle.privacy.rawPayloadsIncluded)
        assertFalse(bundle.privacy.freeFormEvidenceTextIncluded)
        assertFalse(bundle.privacy.activeProbeEvidenceIncluded)
        assertTrue(json.contains("\"schemaVersion\":1"))
    }

    private fun candidate(
        fingerprint: String,
        followingScore: Float,
        relatedFingerprint: String? = null,
        evidence: List<AnalysisEvidenceSummary> = emptyList(),
    ): AnalysisCandidate =
        AnalysisCandidate(
            deviceFingerprint = fingerprint,
            trackingStatus = TrackingStatus.SUSPICIOUS,
            followingScore = followingScore,
            signal = signalSummary(),
            timeBuckets = timeBuckets(),
            locationQuality = locationQuality(),
            movement = movementSummary(),
            encounters = encounterSummary(),
            identityCandidates = identitySummaries(relatedFingerprint),
            representativeEvidence = evidence,
            qualityFlags = listOf(AnalysisQualityFlag.DUPLICATE_SIGNAL_SAMPLES_REDUCED),
            contradictions = contradictionSummaries(relatedFingerprint),
        )

    private fun signalSummary(): AnalysisSignalSummary =
        AnalysisSignalSummary(
            sourceSampleCount = 3,
            reducedSampleCount = 2,
            duplicateSampleCount = 1,
            firstObservedAt = NOW,
            lastObservedAt = NOW + 10_000L,
            rssi =
                AnalysisRssiSummary(
                    sampleCount = 2,
                    minimum = -80,
                    maximum = -60,
                    average = -70.0,
                    median = -70.0,
                ),
        )

    private fun timeBuckets(): List<AnalysisTimeBucket> =
        listOf(
            AnalysisTimeBucket(
                startTimestamp = NOW,
                endTimestampExclusive = NOW + 60_000L,
                sampleCount = 2,
                minimumRssi = -80,
                maximumRssi = -60,
                averageRssi = -70.0,
                usableLocationSampleCount = 1,
            ),
        )

    private fun locationQuality(): AnalysisLocationQualitySummary =
        AnalysisLocationQualitySummary(
            totalSampleCount = 2,
            usableSampleCount = 1,
            rejectedSampleCount = 0,
            missingSampleCount = 1,
            bestAccuracyMeters = 12f,
            worstUsableAccuracyMeters = 12f,
        )

    private fun movementSummary(): AnalysisMovementSummary =
        AnalysisMovementSummary(
            sampleCount = 2,
            movingSampleCount = 2,
            stationarySampleCount = 0,
            unknownSampleCount = 0,
            segments =
                listOf(
                    AnalysisMovementSegment(
                        startTimestamp = NOW,
                        endTimestamp = NOW + 10_000L,
                        sampleCount = 2,
                        state = AnalysisMovementState.MOVING,
                    ),
                ),
        )

    private fun encounterSummary(): AnalysisEncounterSummary =
        AnalysisEncounterSummary(
            historySampleCount = 2,
            firstObservedAt = NOW,
            lastObservedAt = NOW + 10_000L,
            maxEncounterCount = 2,
            peakScore = 0.75f,
            peakTrackingStatus = TrackingStatus.SUSPICIOUS,
            distinctObservedMacCount = 2,
        )

    private fun identitySummaries(relatedFingerprint: String?): List<AnalysisIdentityCandidateSummary> =
        relatedFingerprint?.let { related ->
            listOf(
                AnalysisIdentityCandidateSummary(
                    candidateFingerprint = related,
                    observationCount = 2,
                    firstObservedAt = NOW,
                    lastObservedAt = NOW + 10_000L,
                    maxConfidence = 0.8f,
                    reasonCodes = listOf("WEIGHTED_FEATURE_MATCH"),
                    verdicts = listOf(IdentityCarryoverVerdict.UNREVIEWED),
                    latestVerdict = IdentityCarryoverVerdict.UNREVIEWED,
                    hasCoexistenceEvidence = true,
                ),
            )
        }.orEmpty()

    private fun contradictionSummaries(relatedFingerprint: String?): List<AnalysisContradiction> =
        relatedFingerprint?.let { related ->
            listOf(
                AnalysisContradiction(
                    type = AnalysisContradictionType.IDENTITY_COEXISTENCE,
                    relatedFingerprint = related,
                    description = "Private reducer description is intentionally not bundled.",
                ),
            )
        }.orEmpty()

    private fun evidence(
        source: EvidenceSource,
        reasonText: String,
        isPassive: Boolean,
        provenance: EvidenceProvenance,
    ): AnalysisEvidenceSummary =
        AnalysisEvidenceSummary(
            source = source,
            confidence = DetectionConfidence.HIGH,
            reasonText = reasonText,
            timestamp = NOW,
            isPassive = isPassive,
            provenance = provenance,
            alertEventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
        )

    private companion object {
        private const val SESSION_ID = "session-test-001"
        private const val NOW = 1_790_000_000_000L
    }
}
