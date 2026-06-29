package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.AlertEvidenceEventEntity
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.DetectionEvidence

fun AlertEvidenceEventEntity.toDomain(): AlertEvidenceEvent =
    AlertEvidenceEvent(
        timestamp = timestamp,
        deviceFingerprint = deviceFingerprint,
        observedMac = observedMac,
        eventType = eventType,
        evidence =
            DetectionEvidence(
                source = evidenceSource,
                confidence = confidence,
                reasonText = reasonText,
                timestamp = timestamp,
                rawValue = rawValue,
                parsedValue = parsedValue,
                isPassive = isPassive,
                provenance = evidenceProvenance,
            ),
    )

fun List<AlertEvidenceEventEntity>.toAlertEvidenceEventDomain(): List<AlertEvidenceEvent> =
    map { it.toDomain() }
