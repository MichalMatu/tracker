package io.blueeye.core.model.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ANALYSIS_BUNDLE_SCHEMA_VERSION = 1

/**
 * Versioned, privacy-bounded representation intended for optional higher-level analysis.
 *
 * Hardware addresses, exact coordinates, raw payloads and free-form evidence text are excluded.
 */
@Serializable
data class AnalysisBundleV1(
    val schemaVersion: Int = ANALYSIS_BUNDLE_SCHEMA_VERSION,
    val session: AnalysisBundleSessionV1,
    val privacy: AnalysisBundlePrivacyV1 = AnalysisBundlePrivacyV1(),
    val candidates: List<AnalysisBundleCandidateV1>,
)

@Serializable
data class AnalysisBundleSessionV1(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val candidateCount: Int,
)

@Serializable
data class AnalysisBundlePrivacyV1(
    val identifierMode: String = "session_scoped_alias",
    val exactCoordinatesIncluded: Boolean = false,
    val hardwareAddressesIncluded: Boolean = false,
    val rawPayloadsIncluded: Boolean = false,
    val freeFormEvidenceTextIncluded: Boolean = false,
    val activeProbeEvidenceIncluded: Boolean = false,
)

@Serializable
data class AnalysisBundleCandidateV1(
    val candidateId: String,
    val localVerdict: AnalysisBundleLocalVerdictV1,
    val signal: AnalysisBundleSignalV1,
    val timeBuckets: List<AnalysisBundleTimeBucketV1>,
    val locationQuality: AnalysisBundleLocationQualityV1,
    val movement: AnalysisBundleMovementV1,
    val encounters: AnalysisBundleEncounterV1,
    val identityCandidates: List<AnalysisBundleIdentityCandidateV1>,
    val representativeEvidence: List<AnalysisBundleEvidenceV1>,
    val omittedActiveEvidenceCount: Int,
    val qualityFlags: List<String>,
    val contradictions: List<AnalysisBundleContradictionV1>,
)

@Serializable
data class AnalysisBundleLocalVerdictV1(
    val trackingStatus: String,
    val followingScore: Float,
)

@Serializable
data class AnalysisBundleSignalV1(
    val sourceSampleCount: Int,
    val reducedSampleCount: Int,
    val duplicateSampleCount: Int,
    val firstObservedAt: Long?,
    val lastObservedAt: Long?,
    val rssi: AnalysisBundleRssiV1?,
)

@Serializable
data class AnalysisBundleRssiV1(
    val sampleCount: Int,
    val minimum: Int,
    val maximum: Int,
    val average: Double,
    val median: Double,
)

@Serializable
data class AnalysisBundleTimeBucketV1(
    val startTimestamp: Long,
    val endTimestampExclusive: Long,
    val sampleCount: Int,
    val minimumRssi: Int,
    val maximumRssi: Int,
    val averageRssi: Double,
    val usableLocationSampleCount: Int,
)

@Serializable
data class AnalysisBundleLocationQualityV1(
    val totalSampleCount: Int,
    val usableSampleCount: Int,
    val rejectedSampleCount: Int,
    val missingSampleCount: Int,
    val bestAccuracyMeters: Float?,
    val worstUsableAccuracyMeters: Float?,
)

@Serializable
data class AnalysisBundleMovementV1(
    val sampleCount: Int,
    val movingSampleCount: Int,
    val stationarySampleCount: Int,
    val unknownSampleCount: Int,
    val segments: List<AnalysisBundleMovementSegmentV1>,
)

@Serializable
data class AnalysisBundleMovementSegmentV1(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val sampleCount: Int,
    val state: String,
)

@Serializable
data class AnalysisBundleEncounterV1(
    val historySampleCount: Int,
    val firstObservedAt: Long?,
    val lastObservedAt: Long?,
    val maxEncounterCount: Int,
    val peakScore: Float?,
    val peakTrackingStatus: String?,
    val distinctObservedMacCount: Int,
)

@Serializable
data class AnalysisBundleIdentityCandidateV1(
    val relatedCandidateId: String,
    val observationCount: Int,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val maxConfidence: Float,
    val reasonCodes: List<String>,
    val verdicts: List<String>,
    val latestVerdict: String,
    val hasCoexistenceEvidence: Boolean,
)

@Serializable
data class AnalysisBundleEvidenceV1(
    val source: String,
    val confidence: String,
    val timestamp: Long,
    val isPassive: Boolean,
    val provenance: String,
    val alertEventType: String?,
)

@Serializable
data class AnalysisBundleContradictionV1(
    val type: String,
    val relatedCandidateId: String?,
)

object AnalysisBundleJson {
    private val format =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun encode(bundle: AnalysisBundleV1): String = format.encodeToString(bundle)

    fun decode(serialized: String): AnalysisBundleV1 = format.decodeFromString(serialized)
}
