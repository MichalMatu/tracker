package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.analysis.AnalysisEvidenceSetV1
import io.blueeye.core.model.analysis.AnalysisEvidenceSummaryV1
import io.blueeye.core.model.analysis.AnalysisIdentityDirectionV1
import io.blueeye.core.model.analysis.AnalysisIdentityRelationV1

internal object AnalysisEvidenceReducer {
    fun reduce(
        device: Device,
        alertEvents: List<AlertEvidenceEvent>,
        identityCandidates: List<IdentityContinuityCandidate>,
    ): AnalysisEvidenceSetV1 =
        AnalysisEvidenceSetV1(
            identityRelations = identityRelations(device.fingerprint, identityCandidates),
            representativeEvidence = representativeEvidence(device.evidence, alertEvents),
        )

    fun hasRejectedHighConfidenceRelation(
        relations: List<AnalysisIdentityRelationV1>,
    ): Boolean =
        relations.any { relation ->
            relation.verdict == IdentityCarryoverVerdict.FALSE_MATCH &&
                relation.confidence >= HIGH_CONFIDENCE_IDENTITY_THRESHOLD
        }

    private fun identityRelations(
        candidateKey: String,
        candidates: List<IdentityContinuityCandidate>,
    ): List<AnalysisIdentityRelationV1> =
        candidates
            .asSequence()
            .filter { candidate ->
                candidate.deviceFingerprint == candidateKey ||
                    candidate.candidateFingerprint == candidateKey
            }
            .sortedWith(
                compareBy<IdentityContinuityCandidate>(
                    IdentityContinuityCandidate::timestamp,
                    IdentityContinuityCandidate::candidateFingerprint,
                    IdentityContinuityCandidate::deviceFingerprint,
                    IdentityContinuityCandidate::reasonCode,
                    IdentityContinuityCandidate::id,
                ),
            )
            .map { candidate ->
                val outbound = candidate.deviceFingerprint == candidateKey
                AnalysisIdentityRelationV1(
                    relatedCandidateKey =
                        if (outbound) {
                            candidate.candidateFingerprint
                        } else {
                            candidate.deviceFingerprint
                        },
                    timestamp = candidate.timestamp,
                    reasonCode = candidate.reasonCode,
                    confidence = candidate.confidence,
                    verdict = candidate.verdict,
                    direction =
                        if (outbound) {
                            AnalysisIdentityDirectionV1.OUTBOUND
                        } else {
                            AnalysisIdentityDirectionV1.INBOUND
                        },
                )
            }
            .toList()

    private fun representativeEvidence(
        deviceEvidence: List<DetectionEvidence>,
        alertEvents: List<AlertEvidenceEvent>,
    ): List<AnalysisEvidenceSummaryV1> {
        val latestByIdentity = linkedMapOf<EvidenceIdentity, DetectionEvidence>()
        (deviceEvidence + alertEvents.map(AlertEvidenceEvent::evidence))
            .sortedBy(DetectionEvidence::timestamp)
            .forEach { evidence ->
                latestByIdentity[evidence.identity()] = evidence
            }

        return latestByIdentity.values
            .sortedWith(
                compareByDescending<DetectionEvidence> { it.confidence.ordinal }
                    .thenBy { it.source.name }
                    .thenBy { it.provenance.name }
                    .thenBy(DetectionEvidence::reasonText)
                    .thenByDescending(DetectionEvidence::timestamp),
            )
            .take(MAX_REPRESENTATIVE_EVIDENCE)
            .map { evidence ->
                AnalysisEvidenceSummaryV1(
                    source = evidence.source,
                    confidence = evidence.confidence,
                    provenance = evidence.provenance,
                    reasonText = evidence.reasonText,
                    timestamp = evidence.timestamp,
                    isPassive = evidence.isPassive,
                )
            }
    }

    private fun DetectionEvidence.identity(): EvidenceIdentity =
        EvidenceIdentity(
            source = source.name,
            confidence = confidence.name,
            provenance = provenance.name,
            reasonText = reasonText,
            isPassive = isPassive,
        )

    private data class EvidenceIdentity(
        val source: String,
        val confidence: String,
        val provenance: String,
        val reasonText: String,
        val isPassive: Boolean,
    )

    private const val MAX_REPRESENTATIVE_EVIDENCE = 8
    private const val HIGH_CONFIDENCE_IDENTITY_THRESHOLD = 0.8f
}
