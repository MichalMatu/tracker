package io.blueeye.core.data.db

import androidx.room.TypeConverter
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

/** TypeConverters for alert/evidence history enums. */
class EvidenceConverters {
    @TypeConverter fun fromEvidenceSource(value: EvidenceSource): String = value.name

    @TypeConverter
    fun toEvidenceSource(value: String): EvidenceSource =
        try {
            EvidenceSource.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            EvidenceSource.RAW_PAYLOAD
        }

    @TypeConverter fun fromDetectionConfidence(value: DetectionConfidence): String = value.name

    @TypeConverter
    fun toDetectionConfidence(value: String): DetectionConfidence =
        try {
            DetectionConfidence.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            DetectionConfidence.LOW
        }

    @TypeConverter fun fromAlertEvidenceEventType(value: AlertEvidenceEventType): String = value.name

    @TypeConverter
    fun toAlertEvidenceEventType(value: String): AlertEvidenceEventType =
        try {
            AlertEvidenceEventType.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL
        }

    @TypeConverter fun fromEvidenceProvenance(value: EvidenceProvenance): String = value.name

    @TypeConverter
    fun toEvidenceProvenance(value: String): EvidenceProvenance =
        try {
            EvidenceProvenance.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            EvidenceProvenance.UNKNOWN
        }
}
