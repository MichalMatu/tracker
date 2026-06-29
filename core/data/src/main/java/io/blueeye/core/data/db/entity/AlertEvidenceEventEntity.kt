package io.blueeye.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

/**
 * Append-only alert/evidence event.
 */
@Entity(
    tableName = "alert_evidence_events",
    foreignKeys =
    [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["fingerprint"],
            childColumns = ["deviceFingerprint"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices =
    [
        Index(value = ["deviceFingerprint"]),
        Index(value = ["timestamp"]),
        Index(value = ["deviceFingerprint", "eventType", "timestamp"]),
    ],
)
data class AlertEvidenceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceFingerprint: String,
    val observedMac: String,
    val eventType: AlertEvidenceEventType,
    val timestamp: Long,
    val evidenceSource: EvidenceSource,
    val confidence: DetectionConfidence,
    val reasonText: String,
    val rawValue: String?,
    val parsedValue: String?,
    val isPassive: Boolean,
    val evidenceProvenance: EvidenceProvenance = EvidenceProvenance.UNKNOWN,
)
