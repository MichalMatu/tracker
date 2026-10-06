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
        requireUniqueFingerprints(canonicalCandidates)
        requireFiniteValues(canonicalCandidates)
        val aliases = buildAliases(canonicalCandidates)

        return AnalysisBundleV1(
            session =
                AnalysisBundleSessionV1(
                    sessionId = sessionId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    candidateCount = canonicalCandidates.size,
                ),
            candidates = canonicalCandidates.map { candidate -> candidate.toBundle(aliases) },
        )
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

    private fun buildAliases(candidates: List<AnalysisCandidate>): Map<String, String> {
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
    ): String =
        "$prefix-${(zeroBasedIndex + 1).toString().padStart(ALIAS_DIGITS, '0')}"

    private fun AnalysisCandidate.toBundle(aliases: Map<String, String>): AnalysisBundleCandidateV1 =
        AnalysisBundleCandidateV1(
            candidateId = aliases.getValue(deviceFingerprint),
            localVerdict =
                AnalysisBundleLocalVerdictV1(
                    trackingStatus = trackingStatus.name,
                    followingScore = followingScore,
                ),
            signal = signal.toBundle(),
            timeBuckets = toBundleTimeBuckets(),
            locationQuality = toBundleLocationQuality(),
            movement = toBundleMovement(),
            encounters = toBundleEncounters(),
            identityCandidates = toBundleIdentityCandidates(aliases),
            representativeEvidence = toBundleEvidence(),
            omittedActiveEvidenceCount = representativeEvidence.count(::isActiveProbeEvidence),
            qualityFlags = qualityFlags.map { it.name }.distinct().sorted(),
            contradictions = toBundleContradictions(aliases),
        )

    private fun AnalysisCandidate.toBundleTimeBuckets(): List<AnalysisBundleTimeBucketV1> =
        timeBuckets
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

    private fun AnalysisCandidate.toBundleLocationQuality(): AnalysisBundleLocationQualityV1 =
        AnalysisBundleLocationQualityV1(
            totalSampleCount = locationQuality.totalSampleCount,
            usableSampleCount = locationQuality.usableSampleCount,
            rejectedSampleCount = locationQuality.rejectedSampleCount,
            missingSampleCount = locationQuality.missingSampleCount,
            bestAccuracyMeters = locationQuality.bestAccuracyMeters,
            worstUsableAccuracyMeters = locationQuality.worstUsableAccuracyMeters,
        )

    private fun AnalysisCandidate.toBundleMovement(): AnalysisBundleMovementV1 =
        AnalysisBundleMovementV1(
            sampleCount = movement.sampleCount,
            movingSampleCount = movement.movingSampleCount,
            stationarySampleCount = movement.stationarySampleCount,
            unknownSampleCount = movement.unknownSampleCount,
            segments =
                movement.segments
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

    private fun AnalysisCandidate.toBundleEncounters(): AnalysisBundleEncounterV1 =
        AnalysisBundleEncounterV1(
            historySampleCount = encounters.historySampleCount,
            firstObservedAt = encounters.firstObservedAt,
            lastObservedAt = encounters.lastObservedAt,
            maxEncounterCount = encounters.maxEncounterCount,
            peakScore = encounters.peakScore,
            peakTrackingStatus = encounters.peakTrackingStatus?.name,
            distinctObservedMacCount = encounters.distinctObservedMacCount,
        )

    private fun AnalysisCandidate.toBundleIdentityCandidates(
        aliases: Map<String, String>,
    ): List<AnalysisBundleIdentityCandidateV1> =
        identityCandidates
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

    private fun AnalysisCandidate.toBundleEvidence(): List<AnalysisBundleEvidenceV1> =
        representativeEvidence
            .filterNot(::isActiveProbeEvidence)
            .sortedWith(EVIDENCE_ORDER)
            .map { evidence -> evidence.toBundle() }
            .distinct()

    private fun AnalysisCandidate.toBundleContradictions(
        aliases: Map<String, String>,
    ): List<AnalysisBundleContradictionV1> =
        contradictions
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

    private fun AnalysisSignalSummary.toBundle(): AnalysisBundleSignalV1 =
        AnalysisBundleSignalV1(
            sourceSampleCount = sourceSampleCount,
            reducedSampleCount = reducedSampleCount,
            duplicateSampleCount = duplicateSampleCount,
            firstObservedAt = firstObservedAt,
            lastObservedAt = lastObservedAt,
            rssi =
                rssi?.let { summary ->
                    AnalysisBundleRssiV1(
                        sampleCount = summary.sampleCount,
                        minimum = summary.minimum,
                        maximum = summary.maximum,
                        average = summary.average,
                        median = summary.median,
                    )
                },
        )

    private fun AnalysisEvidenceSummary.toBundle(): AnalysisBundleEvidenceV1 =
        AnalysisBundleEvidenceV1(
            source = source.name,
            confidence = confidence.name,
            timestamp = timestamp,
            isPassive = isPassive,
            provenance = provenance.name,
            alertEventType = alertEventType?.name,
        )

    private fun isActiveProbeEvidence(evidence: AnalysisEvidenceSummary): Boolean =
        evidence.source == EvidenceSource.GATT_PROBE ||
            evidence.source == EvidenceSource.RFCOMM_PROBE ||
            evidence.provenance == EvidenceProvenance.ACTIVE_GATT ||
            evidence.provenance == EvidenceProvenance.ACTIVE_RFCOMM

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

    private const val CANDIDATE_ALIAS_PREFIX = "candidate"
    private const val IDENTITY_ALIAS_PREFIX = "identity"
    private const val ALIAS_DIGITS = 3
    private const val LOW_CONFIDENCE_PRIORITY = 0
    private const val MEDIUM_CONFIDENCE_PRIORITY = 1
    private const val HIGH_CONFIDENCE_PRIORITY = 2
    private const val CRITICAL_CONFIDENCE_PRIORITY = 3
}
