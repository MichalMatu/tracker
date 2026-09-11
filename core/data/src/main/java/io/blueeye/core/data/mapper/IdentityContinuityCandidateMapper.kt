package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
import io.blueeye.core.model.IdentityContinuityCandidate

internal fun IdentityContinuityCandidateEntity.toDomain(): IdentityContinuityCandidate =
    IdentityContinuityCandidate(
        id = id,
        deviceFingerprint = deviceFingerprint,
        candidateFingerprint = candidateFingerprint,
        timestamp = timestamp,
        reasonCode = reasonCode,
        confidence = confidence,
        featureSummary = featureSummary,
        verdict = verdict,
    )

internal fun List<IdentityContinuityCandidateEntity>.toIdentityContinuityCandidateDomain():
    List<IdentityContinuityCandidate> = map(IdentityContinuityCandidateEntity::toDomain)
