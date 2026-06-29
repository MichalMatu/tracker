package io.blueeye.core.alert

import io.blueeye.core.model.DetectionEvidence

data class TacticalDetection(
    val macAddress: String,
    val vendorName: String,
    val category: String,
    val confidence: String,
    val description: String,
    val evidence: DetectionEvidence,
    val rssi: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)
