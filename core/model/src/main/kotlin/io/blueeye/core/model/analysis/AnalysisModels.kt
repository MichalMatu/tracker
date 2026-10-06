package io.blueeye.core.model.analysis

import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.TrackingStatus

/**
 * Stable, privacy-conscious deterministic reduction for one device.
 *
 * Exact observation coordinates and raw packet values are intentionally excluded.
 */
data class AnalysisCandidate(
    val deviceFingerprint: String,
    val trackingStatus: TrackingStatus,
    val followingScore: Float,
    val signal: AnalysisSignalSummary,
    val timeBuckets: List<AnalysisTimeBucket>,
    val locationQuality: AnalysisLocationQualitySummary,
    val movement: AnalysisMovementSummary,
    val encounters: AnalysisEncounterSummary,
    val identityCandidates: List<AnalysisIdentityCandidateSummary>,
    val representativeEvidence: List<AnalysisEvidenceSummary>,
    val qualityFlags: List<AnalysisQualityFlag>,
    val contradictions: List<AnalysisContradiction>,
)

data class AnalysisSignalSummary(
    val sourceSampleCount: Int,
    val reducedSampleCount: Int,
    val duplicateSampleCount: Int,
    val firstObservedAt: Long?,
    val lastObservedAt: Long?,
    val rssi: AnalysisRssiSummary?,
)

data class AnalysisRssiSummary(
    val sampleCount: Int,
    val minimum: Int,
    val maximum: Int,
    val average: Double,
    val median: Double,
)

data class AnalysisTimeBucket(
    val startTimestamp: Long,
    val endTimestampExclusive: Long,
    val sampleCount: Int,
    val minimumRssi: Int,
    val maximumRssi: Int,
    val averageRssi: Double,
    val usableLocationSampleCount: Int,
)

data class AnalysisLocationQualitySummary(
    val totalSampleCount: Int,
    val usableSampleCount: Int,
    val rejectedSampleCount: Int,
    val missingSampleCount: Int,
    val bestAccuracyMeters: Float?,
    val worstUsableAccuracyMeters: Float?,
)

enum class AnalysisMovementState {
    MOVING,
    STATIONARY,
    UNKNOWN,
}

data class AnalysisMovementSegment(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val sampleCount: Int,
    val state: AnalysisMovementState,
)

data class AnalysisMovementSummary(
    val sampleCount: Int,
    val movingSampleCount: Int,
    val stationarySampleCount: Int,
    val unknownSampleCount: Int,
    val segments: List<AnalysisMovementSegment>,
)

data class AnalysisEncounterSummary(
    val historySampleCount: Int,
    val firstObservedAt: Long?,
    val lastObservedAt: Long?,
    val maxEncounterCount: Int,
    val peakScore: Float?,
    val peakTrackingStatus: TrackingStatus?,
    val distinctObservedMacCount: Int,
)

data class AnalysisIdentityCandidateSummary(
    val candidateFingerprint: String,
    val observationCount: Int,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val maxConfidence: Float,
    val reasonCodes: List<String>,
    val verdicts: List<IdentityCarryoverVerdict>,
    val latestVerdict: IdentityCarryoverVerdict,
    val hasCoexistenceEvidence: Boolean,
)

data class AnalysisEvidenceSummary(
    val source: EvidenceSource,
    val confidence: DetectionConfidence,
    val reasonText: String,
    val timestamp: Long,
    val isPassive: Boolean,
    val provenance: EvidenceProvenance,
    val alertEventType: AlertEvidenceEventType?,
)

enum class AnalysisQualityFlag {
    NO_SIGNAL_SAMPLES,
    DUPLICATE_SIGNAL_SAMPLES_REDUCED,
    NO_LOCATION_DATA,
    NO_USABLE_LOCATION,
    REJECTED_LOCATION_SAMPLES,
    NO_FOLLOW_ME_HISTORY,
}

enum class AnalysisContradictionType {
    IDENTITY_COEXISTENCE,
    IDENTITY_VERDICT_CONFLICT,
}

data class AnalysisContradiction(
    val type: AnalysisContradictionType,
    val relatedFingerprint: String?,
    val description: String,
)
