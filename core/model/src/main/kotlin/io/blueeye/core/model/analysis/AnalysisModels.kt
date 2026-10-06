package io.blueeye.core.model.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus

data class AnalysisInput(
    val device: Device,
    val signalSamples: List<SignalSample>,
    val followMeHistory: List<FollowMeHistorySample>,
    val alertEvidenceEvents: List<AlertEvidenceEvent>,
    val identityCandidates: List<IdentityContinuityCandidate>,
)

data class DeterministicAnalysisCandidate(
    val deviceFingerprint: String,
    val localTrackingStatus: TrackingStatus,
    val localFollowingScore: Float,
    val window: AnalysisWindow,
    val signal: AnalysisSignalSummary,
    val locationQuality: AnalysisLocationQualitySummary,
    val movement: AnalysisMovementSummary,
    val identity: AnalysisIdentitySummary,
    val timeBuckets: List<AnalysisTimeBucket>,
    val representativeEvidence: List<AnalysisRepresentativeEvidence>,
    val qualityFlags: List<AnalysisQualityFlag>,
    val contradictions: List<AnalysisContradiction>,
)

data class AnalysisWindow(
    val startTimestamp: Long,
    val endTimestamp: Long,
)

data class AnalysisSignalSummary(
    val sourceSampleCount: Int,
    val uniqueSampleCount: Int,
    val acceptedSampleCount: Int,
    val duplicateSampleCount: Int,
    val rejectedSampleCount: Int,
    val minRssi: Int?,
    val maxRssi: Int?,
    val averageRssi: Double?,
    val medianRssi: Double?,
)

data class AnalysisLocationQualitySummary(
    val sourceLocationCount: Int,
    val usableLocationCount: Int,
    val rejectedLocationCount: Int,
    val bestAccuracyMeters: Float?,
    val worstAccuracyMeters: Float?,
    val averageAccuracyMeters: Double?,
)

data class AnalysisMovementSummary(
    val sourceObservationCount: Int,
    val encounterSegmentCount: Int,
    val movingSegmentCount: Int,
    val movingObservationCount: Int,
    val stationaryObservationCount: Int,
    val unknownMovementObservationCount: Int,
    val maxEncounterCount: Int,
)

data class AnalysisIdentitySummary(
    val sourceCandidateCount: Int,
    val omittedCandidateCount: Int,
    val candidates: List<AnalysisIdentityCandidate>,
)

data class AnalysisIdentityCandidate(
    val candidateFingerprint: String,
    val timestamp: Long,
    val reasonCode: String,
    val confidence: Float,
    val verdict: IdentityCarryoverVerdict,
    val disposition: AnalysisIdentityDisposition,
)

enum class AnalysisIdentityDisposition {
    CONFIRMED_CONTINUITY,
    POSSIBLE_CONTINUITY,
    INCONCLUSIVE,
    REJECTED_BY_REVIEW,
    COEXISTENCE_CONFLICT,
}

data class AnalysisTimeBucket(
    val startTimestamp: Long,
    val endExclusiveTimestamp: Long,
    val sampleCount: Int,
    val minRssi: Int,
    val maxRssi: Int,
    val averageRssi: Double,
    val usableLocationCount: Int,
)

data class AnalysisRepresentativeEvidence(
    val source: EvidenceSource,
    val confidence: DetectionConfidence,
    val reasonText: String,
    val timestamp: Long,
    val parsedValue: String?,
    val isPassive: Boolean,
    val provenance: EvidenceProvenance,
    val alertEventType: AlertEvidenceEventType?,
)

enum class AnalysisQualityFlag {
    DUPLICATES_REDUCED,
    INVALID_SIGNAL_SAMPLES,
    NO_SIGNAL_SAMPLES,
    NO_USABLE_LOCATION,
    PARTIAL_LOCATION_COVERAGE,
    INVALID_LOCATION_SAMPLES,
    NO_FOLLOW_ME_HISTORY,
    MOVEMENT_UNKNOWN,
    IDENTITY_COEXISTENCE_DETECTED,
    IDENTITY_CANDIDATES_TRUNCATED,
    REPRESENTATIVE_EVIDENCE_TRUNCATED,
}

data class AnalysisContradiction(
    val type: AnalysisContradictionType,
    val detail: String,
)

enum class AnalysisContradictionType {
    IDENTITY_COEXISTENCE,
    IDENTITY_REVIEW_CONFLICT,
    TRACKING_STATUS_DIVERGENCE,
    FOLLOW_ME_WITHOUT_MOVEMENT,
}
