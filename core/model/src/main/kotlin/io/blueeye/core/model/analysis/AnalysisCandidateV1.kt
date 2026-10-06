package io.blueeye.core.model.analysis

import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.TrackingStatus

/**
 * Stable local analysis representation for one device candidate.
 *
 * V1 intentionally excludes explicit MAC fields, exact coordinates and raw Bluetooth payloads.
 * localCandidateKey is an app-local fingerprint and must be remapped before any external bundle.
 */
data class AnalysisCandidateV1(
    val localCandidateKey: String,
    val window: AnalysisWindowV1,
    val localAssessment: AnalysisLocalAssessmentV1,
    val signal: AnalysisSignalSummaryV1,
    val followMe: AnalysisFollowMeSummaryV1,
    val identity: AnalysisIdentitySummaryV1,
    val diagnostics: AnalysisDiagnosticsV1,
)

data class AnalysisWindowV1(
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

data class AnalysisLocalAssessmentV1(
    val trackingStatus: TrackingStatus,
    val followingScore: Float,
    val calibrationLabel: DeviceCalibrationLabel,
    val isWatchlisted: Boolean,
    val isTrackingEnabled: Boolean,
    val isIgnoredForTracking: Boolean,
    val isSafeBeacon: Boolean,
)

data class AnalysisSignalSummaryV1(
    val sampleCount: Int,
    val minRssi: Int?,
    val maxRssi: Int?,
    val averageRssi: Double?,
    val totalBucketCount: Int,
    val buckets: List<AnalysisSignalBucketV1>,
    val locationQuality: AnalysisLocationQualityV1,
)

data class AnalysisSignalBucketV1(
    val startedAt: Long,
    val sampleCount: Int,
    val minRssi: Int,
    val maxRssi: Int,
    val averageRssi: Double,
)

data class AnalysisLocationQualityV1(
    val samplesWithCoordinates: Int,
    val usableLocationSamples: Int,
    val poorLocationSamples: Int,
    val bestAccuracyMeters: Float?,
    val worstAccuracyMeters: Float?,
)

data class AnalysisFollowMeSummaryV1(
    val observationCount: Int,
    val maxScore: Float?,
    val latestStatus: TrackingStatus?,
    val maxEncounterCount: Int,
    val movedObservationCount: Int,
    val baselineObservationCount: Int,
)

data class AnalysisIdentitySummaryV1(
    val totalRelationCount: Int,
    val relations: List<AnalysisIdentityRelationV1>,
)

data class AnalysisIdentityRelationV1(
    val relatedLocalCandidateKey: String,
    val timestamp: Long,
    val reasonCode: String,
    val confidence: Float,
    val verdict: IdentityCarryoverVerdict,
    val featureSummary: String,
)

data class AnalysisEvidenceSummaryV1(
    val source: EvidenceSource,
    val confidence: DetectionConfidence,
    val provenance: EvidenceProvenance,
    val reasonText: String,
    val timestamp: Long,
    val eventType: AlertEvidenceEventType?,
)

data class AnalysisDiagnosticsV1(
    val representativeEvidence: List<AnalysisEvidenceSummaryV1>,
    val qualityFlags: List<AnalysisQualityFlagV1>,
    val contradictions: List<AnalysisContradictionV1>,
)

enum class AnalysisQualityFlagV1 {
    NO_SIGNAL_SAMPLES,
    OUT_OF_SCOPE_SIGNAL_SAMPLES_DROPPED,
    SIGNAL_BUCKETS_TRUNCATED,
    IDENTITY_RELATIONS_TRUNCATED,
    NO_LOCATION_DATA,
    POOR_LOCATION_QUALITY,
    SIGNAL_HISTORY_INCOMPLETE,
    FOLLOW_ME_HISTORY_INCOMPLETE,
    IDENTITY_HISTORY_INCOMPLETE,
}

enum class AnalysisContradictionV1 {
    CALIBRATED_SAFE_BUT_LOCAL_ATTENTION_ACTIVE,
    BASELINE_OBSERVATION_WITH_DANGEROUS_STATUS,
    REJECTED_IDENTITY_RELATION_PRESENT,
}
