package io.blueeye.core.model.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus

/**
 * Explicit reducer input. Completeness flags are part of the contract because several current
 * history queries are intentionally bounded and must not be mistaken for a complete event stream.
 */
data class AnalysisInputV1(
    val device: Device,
    val signalSamples: List<SignalSample>,
    val followMeHistory: List<FollowMeHistorySample>,
    val alertEvents: List<AlertEvidenceEvent>,
    val identityCandidates: List<IdentityContinuityCandidate>,
    val completeness: AnalysisHistoryCompletenessV1 = AnalysisHistoryCompletenessV1(),
)

data class AnalysisHistoryCompletenessV1(
    val signalHistoryComplete: Boolean = false,
    val followMeHistoryComplete: Boolean = false,
    val alertHistoryComplete: Boolean = false,
    val identityHistoryComplete: Boolean = false,
    val deviceEvidenceHistoryComplete: Boolean = false,
)

data class AnalysisCandidateV1(
    val candidateKey: String,
    val window: AnalysisTimeWindowV1,
    val verdict: AnalysisLocalVerdictV1,
    val observations: AnalysisObservationSummaryV1,
    val evidence: AnalysisEvidenceSetV1,
    val diagnostics: AnalysisDiagnosticsV1,
)

data class AnalysisTimeWindowV1(
    val startedAt: Long,
    val endedAt: Long,
)

data class AnalysisLocalVerdictV1(
    val trackingStatus: TrackingStatus,
    val followingScore: Float,
    val calibrationLabel: DeviceCalibrationLabel,
    val userSuppressed: Boolean,
)

data class AnalysisObservationSummaryV1(
    val signal: AnalysisSignalSummaryV1,
    val movement: AnalysisMovementSummaryV1,
    val alerts: AnalysisAlertSummaryV1,
)

data class AnalysisSignalSummaryV1(
    val sourceSampleCount: Int,
    val uniqueSampleCount: Int,
    val rssi: AnalysisRssiSummaryV1,
    val location: AnalysisLocationQualityV1,
    val buckets: List<AnalysisSignalBucketV1>,
)

data class AnalysisRssiSummaryV1(
    val sampleCount: Int,
    val minimum: Int?,
    val maximum: Int?,
    val average: Double?,
)

data class AnalysisLocationQualityV1(
    val samplesWithCoordinates: Int,
    val usableSamples: Int,
    val poorSamples: Int,
    val bestUsableAccuracyMeters: Float?,
    val worstUsableAccuracyMeters: Float?,
)

data class AnalysisSignalBucketV1(
    val bucketStartedAt: Long,
    val sampleCount: Int,
    val minimumRssi: Int,
    val maximumRssi: Int,
    val averageRssi: Double,
    val usableLocationSampleCount: Int,
)

data class AnalysisMovementSummaryV1(
    val sourceSampleCount: Int,
    val maxScore: Float?,
    val maxEncounterCount: Int?,
    val userMovedSampleCount: Int,
    val baselineSampleCount: Int,
    val segments: List<AnalysisEncounterSegmentV1>,
)

data class AnalysisEncounterSegmentV1(
    val startedAt: Long,
    val endedAt: Long,
    val sampleCount: Int,
    val maxScore: Float,
    val maxEncounterCount: Int,
    val userMovedObserved: Boolean,
)

data class AnalysisAlertSummaryV1(
    val totalCount: Int,
    val watchlistReturnCount: Int,
    val publicSafetyCount: Int,
    val followMeAlertCount: Int,
    val firstAlertAt: Long?,
    val lastAlertAt: Long?,
) {
    fun count(eventType: AlertEvidenceEventType): Int =
        when (eventType) {
            AlertEvidenceEventType.WATCHLIST_RETURN -> watchlistReturnCount
            AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL -> publicSafetyCount
            AlertEvidenceEventType.FOLLOW_ME_ALERT -> followMeAlertCount
        }
}

data class AnalysisEvidenceSetV1(
    val identityRelations: List<AnalysisIdentityRelationV1>,
    val representativeEvidence: List<AnalysisEvidenceSummaryV1>,
)

data class AnalysisIdentityRelationV1(
    val relatedCandidateKey: String,
    val timestamp: Long,
    val reasonCode: String,
    val confidence: Float,
    val verdict: IdentityCarryoverVerdict,
    val direction: AnalysisIdentityDirectionV1,
)

enum class AnalysisIdentityDirectionV1 {
    OUTBOUND,
    INBOUND,
}

data class AnalysisEvidenceSummaryV1(
    val source: EvidenceSource,
    val confidence: DetectionConfidence,
    val provenance: EvidenceProvenance,
    val reasonText: String,
    val timestamp: Long,
    val isPassive: Boolean,
)

data class AnalysisDiagnosticsV1(
    val contradictions: List<AnalysisContradictionV1>,
    val qualityFlags: List<AnalysisQualityFlagV1>,
)

enum class AnalysisContradictionV1 {
    USER_SUPPRESSION_CONFLICTS_WITH_LOCAL_ATTENTION,
    REVIEW_REJECTS_HIGH_CONFIDENCE_IDENTITY_RELATION,
}

enum class AnalysisQualityFlagV1 {
    NO_SIGNAL_SAMPLES,
    NO_FOLLOW_ME_HISTORY,
    NO_LOCATION_DATA,
    POOR_LOCATION_ACCURACY,
    SIGNAL_HISTORY_INCOMPLETE,
    FOLLOW_ME_HISTORY_INCOMPLETE,
    ALERT_HISTORY_INCOMPLETE,
    IDENTITY_HISTORY_INCOMPLETE,
    DEVICE_EVIDENCE_HISTORY_INCOMPLETE,
    FOREIGN_DEVICE_INPUT_DROPPED,
}
