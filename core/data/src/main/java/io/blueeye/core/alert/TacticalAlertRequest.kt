package io.blueeye.core.alert

import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

data class TacticalAlertRequest(
    val macAddress: String,
    val rssi: Int,
    val match: TacticalOuiInfo,
    val evidenceSource: EvidenceSource,
    val rawEvidenceValue: String,
    val evidenceProvenance: EvidenceProvenance,
    val evidence: DetectionEvidence? = null,
)
