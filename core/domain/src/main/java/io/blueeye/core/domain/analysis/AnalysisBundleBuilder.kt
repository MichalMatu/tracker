package io.blueeye.core.domain.analysis

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.analysis.AnalysisBundleCandidateV1
import io.blueeye.core.model.analysis.AnalysisBundleContradictionV1
import io.blueeye.core.model.analysis.AnalysisBundleEncounterV1
import io.blueeye.core.model.analysis.AnalysisBundleEvidenceV1
import io.blueeye.core.model.analysis.AnalysisBundleIdentityCandidateV1
import io.blueeye.core.model.analysis.AnalysisBundleLocalVerdictV1
import io.blueeye.core.model.analysis.AnalysisBundleLocationQualityV1
import io.blueeye.core.model.analysis.AnalysisBundleMovementSegmentV1
import io.blueeye.core.model.analysis.AnalysisBundleMovementV1
import io.blueeye.core.model.analysis.AnalysisBundleRssiV1
import io.blueeye.core.model.analysis.AnalysisBundleSessionV1
import io.blueeye.core.model.analysis.AnalysisBundleSignalV1
import io.blueeye.core.model.analysis.AnalysisBundleTimeBucketV1
import io.blueeye.core.model.analysis.AnalysisBundleV1
import io.blueeye.core.model.analysis.AnalysisCandidate
import io.blueeye.core.model.analysis.AnalysisEvidenceSummary
import io.blueeye.core.model.analysis.AnalysisSignalSummary

/**
 * Builds a deterministic, privacy-bounded V1 bundle from already reduced analysis candidates.
 *
 * No wall clock, database, Android API or raw observation input is consulted here.
 */
object AnalysisBundleBuilder {
    fun build(
        sessionId: String,
        startedAt: Long,
        endedAt: Long,
        candidates: List<AnalysisCandidate>,
    ): AnalysisBundleV1 {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(startedAt <= endedAt) { "session range must be ordered" }

        val canonicalCandidates = candidates.sortedBy(AnalysisCandidate::deviceFingerprint)
        AnalysisBundleValidator.validate(canonicalCandidates)
        val aliases = AnalysisBundleAliasFactory.build(canonicalCandidates)

        return AnalysisBundleV1(
            session =
                AnalysisBundleSessionV1(
                    sessionId = sessionId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    candidateCount = canonicalCandidates.size,
                ),
            candidates =
                canonicalCandidates.map { candidate ->
                    AnalysisBundleCandidateMapper.map(candidate, aliases)
                },
        )
    }
}

private object AnalysisBundleValidator {
    fun validate(candidates: List<AnalysisCandidate>) {
        requireUniqueFingerprints(candidates)
        requireFiniteValues(candidates)
    }

    private fun requireUniqueFingerprints(candidates: List<AnalysisCandidate>) {
        val fingerprints = candidates.map(AnalysisCandidate::deviceFingerprint)
        require(fingerprints.all(String::isNotBlank)) {
            "candidate fingerprint must not be blank"
        }
        require(fingerprints.distinct().size == fingerprints.size) {
            "candidate fingerprints must be unique within a bundle"
        }
        require(
            candidates
                .flatMap { candidate -> candidate.identityCandidates }
                .all { identity -> identity.candidateFingerprint.isNotBlank() },
        ) {
            "identity candidate fingerprint must not be blank"
        }
    }

    private fun requireFiniteValues(candidates: List<AnalysisCandidate>) {
        candidates.forEach { candidate ->
            require(candidate.followingScore.isFinite()) { "following score must be finite" }
            require(candidate.encounters.peakScore?.isFinite() != false) {
                "peak encounter score must be finite"
            }
            require(
                candidate.identityCandidates.all { identity ->
                    identity.maxConfidence.isFinite()
                },
            ) {
                "identity confidence must be finite"
            }
        }
    }
}

private object AnalysisBundleAliasFactory {
    fun build(candidates: List<AnalysisCandidate>): Map<String, String> {
        val primaryFingerprints =
            candidates
                .map(AnalysisCandidate::deviceFingerprint)
                .distinct()
                .sorted()
        val primarySet = primaryFingerprints.toSet()
        val relatedFingerprints =
            candidates
                .flatMap { candidate ->
                    candidate.identityCandidates.map { it.candidateFingerprint } +
                        candidate.contradictions.mapNotNull { it.relatedFingerprint }
                }
                .filter { fingerprint -> fingerprint !in primarySet }
                .distinct()
                .sorted()

        return buildMap {
            primaryFingerprints.forEachIndexed { index, fingerprint ->
                put(fingerprint, alias(CANDIDATE_ALIAS_PREFIX, index))
            }
            relatedFingerprints.forEachIndexed { index, fingerprint ->
                put(fingerprint, alias(IDENTITY_ALIAS_PREFIX, index))
            }
        }
    }

    private fun alias(
        prefix: String,
        zeroBasedIndex: Int,
    ): String = "$prefix-${(zeroBasedIndex + 1).toString().padStart(ALIAS_DIGITS, '0')}"

    private const val CANDIDATE_ALIAS_PREFIX = "candidate"
    private const val IDENTITY_ALIAS_PREFIX = "identity"
    private const val ALIAS_DIGITS = 3
}

private object AnalysisBundleCandidateMapper {
    fun map(
        candidate: AnalysisCandidate,
        aliases: Map<String, String>,
    ): AnalysisBundleCandidateV1 =
        AnalysisBundleCandidateV1(
            candidateId = aliases.getValue(candidate.deviceFingerprint),
            localVerdict =
                AnalysisBundleLocalVerdictV1(
                    trackingStatus = candidate.trackingStatus.name,
                    followingScore = candidate.followingScore,
                ),
            signal = signal(candidate.signal),
            timeBuckets = timeBuckets(candidate),
            locationQuality = locationQuality(candidate),
            movement = movement(candidate),
            encounters = encounters(candidate),
            identityCandidates = identityCandidates(candidate, aliases),
            representativeEvidence = AnalysisBundleEvidenceMapper.map(candidate),
            omittedActiveEvidenceCount =
                candidate.representativeEvidence.count(AnalysisBundleEvidenceMapper::isActive),
            qualityFlags = candidate.qualityFlags.map { it.name }.distinct().sorted(),
            contradictions = contradictions(candidate, aliases),
        )

    private fun timeBuckets(candidate: AnalysisCandidate): List<AnalysisBundleTimeBucketV1> =
        candidate.timeBuckets
            .sortedWith(
                compareBy(
                    { it.startTimestamp },
                    { it.endTimestampExclusive },
                    { it.minimumRssi },
                    { it.maximumRssi },
                ),
            )
            .map { bucket ->
                AnalysisBundleTimeBucketV1(
                    startTimestamp = bucket.startTimestamp,
                    endTimestampExclusive = bucket.endTimestampExclusive,
                    sampleCount = bucket.sampleCount,
                    minimumRssi = bucket.minimumRssi,
                    maximumRssi = bucket.maximumRssi,
                    averageRssi = bucket.averageRssi,
                    usableLocationSampleCount = bucket.usableLocationSampleCount,
                )
            }

    private fun locationQuality(candidate: AnalysisCandidate): AnalysisBundleLocationQualityV1 =
        AnalysisBundleLocationQualityV1(
            totalSampleCount = candidate.locationQuality.totalSampleCount,
            usableSampleCount = candidate.locationQuality.usableSampleCount,
            rejectedSampleCount = candidate.locationQuality.rejectedSampleCount,
            missingSampleCount = candidate.locationQuality.missingSampleCount,
            bestAccuracyMeters = candidate.locationQuality.bestAccuracyMeters,
            worstUsableAccuracyMeters = candidate.locationQuality.worstUsableAccuracyMeters,
        )

    private fun movement(candidate: AnalysisCandidate): AnalysisBundleMovementV1 =
        AnalysisBundleMovementV1(
            sampleCount = candidate.movement.sampleCount,
            movingSampleCount = candidate.movement.movingSampleCount,
            stationarySampleCount = candidate.movement.stationarySampleCount,
            unknownSampleCount = candidate.movement.unknownSampleCount,
            segments =
                candidate.movement.segments
                    .sortedWith(
                        compareBy(
                            { it.startTimestamp },
                            { it.endTimestamp },
                            { it.state.name },
                        ),
                    )
                    .map { segment ->
                        AnalysisBundleMovementSegmentV1(
                            startTimestamp = segment.startTimestamp,
                            endTimestamp = segment.endTimestamp,
                            sampleCount = segment.sampleCount,
                            state = segment.state.name,
                        )
                    },
        )

    private fun encounters(candidate: AnalysisCandidate): AnalysisBundleEncounterV1 =
        AnalysisBundleEncounterV1(
            historySampleCount = candidate.encounters.historySampleCount,
            firstObservedAt = candidate.encounters.firstObservedAt,
            lastObservedAt = candidate.encounters.lastObservedAt,
            maxEncounterCount = candidate.encounters.maxEncounterCount,
            peakScore = candidate.encounters.peakScore,
            peakTrackingStatus = candidate.encounters.peakTrackingStatus?.name,
            distinctObservedMacCount = candidate.encounters.distinctObservedMacCount,
        )

    private fun identityCandidates(
        candidate: AnalysisCandidate,
        aliases: Map<String, String>,
    ): List<AnalysisBundleIdentityCandidateV1> =
        candidate.identityCandidates
            .map { identity ->
                AnalysisBundleIdentityCandidateV1(
                    relatedCandidateId = aliases.getValue(identity.candidateFingerprint),
                    observationCount = identity.observationCount,
                    firstObservedAt = identity.firstObservedAt,
                    lastObservedAt = identity.lastObservedAt,
                    maxConfidence = identity.maxConfidence,
                    reasonCodes = identity.reasonCodes.distinct().sorted(),
                    verdicts = identity.verdicts.map { it.name }.distinct().sorted(),
                    latestVerdict = identity.latestVerdict.name,
                    hasCoexistenceEvidence = identity.hasCoexistenceEvidence,
                )
            }
            .sortedWith(
                compareBy(
                    AnalysisBundleIdentityCandidateV1::relatedCandidateId,
                    AnalysisBundleIdentityCandidateV1::firstObservedAt,
                    AnalysisBundleIdentityCandidateV1::lastObservedAt,
                ),
            )

    private fun contradictions(
        candidate: AnalysisCandidate,
        aliases: Map<String, String>,
    ): List<AnalysisBundleContradictionV1> =
        candidate.contradictions
            .map { contradiction ->
                AnalysisBundleContradictionV1(
                    type = contradiction.type.name,
                    relatedCandidateId =
                        contradiction.relatedFingerprint?.let(aliases::getValue),
                )
            }
            .distinct()
            .sortedWith(
                compareBy(
                    AnalysisBundleContradictionV1::type,
                    { it.relatedCandidateId.orEmpty() },
                ),
            )

    private fun signal(summary: AnalysisSignalSummary): AnalysisBundleSignalV1 =
        AnalysisBundleSignalV1(
            sourceSampleCount = summary.sourceSampleCount,
            reducedSampleCount = summary.reducedSampleCount,
            duplicateSampleCount = summary.duplicateSampleCount,
            firstObservedAt = summary.firstObservedAt,
            lastObservedAt = summary.lastObservedAt,
            rssi =
                summary.rssi?.let { rssi ->
                    AnalysisBundleRssiV1(
                        sampleCount = rssi.sampleCount,
                        minimum = rssi.minimum,
                        maximum = rssi.maximum,
                        average = rssi.average,
                        median = rssi.median,
                    )
                },
        )
}

private object AnalysisBundleEvidenceMapper {
    fun map(candidate: AnalysisCandidate): List<AnalysisBundleEvidenceV1> =
        candidate.representativeEvidence
            .filterNot(::isActive)
            .sortedWith(EVIDENCE_ORDER)
            .map { evidence -> toBundle(evidence) }
            .distinct()

    fun isActive(evidence: AnalysisEvidenceSummary): Boolean =
        evidence.source == EvidenceSource.GATT_PROBE ||
            evidence.source == EvidenceSource.RFCOMM_PROBE ||
            evidence.provenance == EvidenceProvenance.ACTIVE_GATT ||
            evidence.provenance == EvidenceProvenance.ACTIVE_RFCOMM

    private fun toBundle(evidence: AnalysisEvidenceSummary): AnalysisBundleEvidenceV1 =
        AnalysisBundleEvidenceV1(
            source = evidence.source.name,
            confidence = evidence.confidence.name,
            timestamp = evidence.timestamp,
            isPassive = evidence.isPassive,
            provenance = evidence.provenance.name,
            alertEventType = evidence.alertEventType?.name,
        )

    private fun confidencePriority(confidence: DetectionConfidence): Int =
        when (confidence) {
            DetectionConfidence.LOW -> LOW_CONFIDENCE_PRIORITY
            DetectionConfidence.MEDIUM -> MEDIUM_CONFIDENCE_PRIORITY
            DetectionConfidence.HIGH -> HIGH_CONFIDENCE_PRIORITY
            DetectionConfidence.CRITICAL -> CRITICAL_CONFIDENCE_PRIORITY
        }

    private val EVIDENCE_ORDER =
        compareByDescending<AnalysisEvidenceSummary> { evidence ->
            confidencePriority(evidence.confidence)
        }.thenByDescending(AnalysisEvidenceSummary::timestamp)
            .thenBy { it.source.name }
            .thenBy { it.provenance.name }
            .thenBy { it.alertEventType?.name.orEmpty() }
            .thenBy(AnalysisEvidenceSummary::isPassive)

    private const val LOW_CONFIDENCE_PRIORITY = 0
    private const val MEDIUM_CONFIDENCE_PRIORITY = 1
    private const val HIGH_CONFIDENCE_PRIORITY = 2
    private const val CRITICAL_CONFIDENCE_PRIORITY = 3
}
