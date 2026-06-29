package io.blueeye.core.model

/**
 * Durable alert/evidence event shown in history and future exports.
 */
data class AlertEvidenceEvent(
    val timestamp: Long,
    val deviceFingerprint: String,
    val observedMac: String,
    val eventType: AlertEvidenceEventType,
    val evidence: DetectionEvidence,
)

enum class AlertEvidenceEventType {
    WATCHLIST_RETURN,
    PUBLIC_SAFETY_SIGNAL,
    FOLLOW_ME_ALERT,
}
