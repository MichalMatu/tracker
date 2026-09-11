package io.blueeye.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.blueeye.core.model.IdentityCarryoverVerdict

/** Durable, non-destructive relation between two records that may represent one physical device. */
@Entity(
    tableName = "identity_continuity_candidates",
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
        Index(value = ["candidateFingerprint"]),
        Index(value = ["timestamp"]),
    ],
)
data class IdentityContinuityCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceFingerprint: String,
    val candidateFingerprint: String,
    val timestamp: Long,
    val reasonCode: String,
    val confidence: Float,
    val featureSummary: String,
    val verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
)
