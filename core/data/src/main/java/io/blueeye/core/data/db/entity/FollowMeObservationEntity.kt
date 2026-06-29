package io.blueeye.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.blueeye.core.model.TrackingStatus

/**
 * Append-only Follow-Me score snapshot.
 *
 * This preserves why a score changed instead of keeping only the latest device-row verdict.
 */
@Entity(
    tableName = "follow_me_observations",
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
        Index(value = ["deviceFingerprint", "timestamp"]),
    ],
)
data class FollowMeObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceFingerprint: String,
    val observedMac: String,
    val timestamp: Long,
    val trackingStatus: TrackingStatus,
    val score: Float,
    val explanation: String?,
    val rssi: Int,
    val encounterCount: Int,
    val durationScore: Int,
    val rssiStabilityScore: Int,
    val deviceTypeScore: Int,
    val macBehaviorScore: Int,
    val encounterScore: Int,
    val userMoved: Boolean?,
    val baselineDevice: Boolean?,
)
