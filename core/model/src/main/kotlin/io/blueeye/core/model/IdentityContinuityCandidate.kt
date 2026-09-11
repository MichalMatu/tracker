package io.blueeye.core.model

/** Review-only identity continuity relation; it never implies an automatic merge. */
data class IdentityContinuityCandidate(
    val id: Long,
    val deviceFingerprint: String,
    val candidateFingerprint: String,
    val timestamp: Long,
    val reasonCode: String,
    val confidence: Float,
    val featureSummary: String,
    val verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
)
