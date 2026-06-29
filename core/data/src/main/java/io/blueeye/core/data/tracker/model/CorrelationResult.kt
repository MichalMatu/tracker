package io.blueeye.core.data.tracker.model

/** Machine-readable explanation for a MAC carryover match. */
enum class CarryoverMatchReason {
    APPLE_SHADOW,
    MICROSOFT_SHADOW,
    SAME_NAME_PROXIMITY,
    WEIGHTED_FEATURE_MATCH,
}

/** Evidence produced by the carryover matcher for later persistence and UI explanation. */
data class CarryoverMatchEvidence(
    val reasonCode: CarryoverMatchReason,
    val confidence: Float,
    val featureSummary: String,
)

/** Target match with the matcher evidence that justified it. */
data class CarryoverMatch(
    val targetId: String,
    val evidence: CarryoverMatchEvidence,
)

/** Result of correlation attempt. */
data class CorrelationResult(
    val targetId: String,
    val isNewTarget: Boolean,
    val isCarryover: Boolean,
    val isPending: Boolean = false,
    val correlatedMac: String?,
    val macChangeCount: Int = 0,
    val matchEvidence: CarryoverMatchEvidence? = null,
)
