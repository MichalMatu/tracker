package io.blueeye.core.model

data class PublicSafetySignal(
    val deviceId: String,
    val vendorName: String,
    val category: String,
    val confidence: DetectionConfidence,
    val description: String,
    val evidence: List<DetectionEvidence>,
    val rssi: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)
