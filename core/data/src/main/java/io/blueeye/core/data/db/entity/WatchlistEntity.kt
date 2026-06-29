package io.blueeye.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.blueeye.core.model.AlertType

/**
 * Wpis na liście obserwowanych (Watchlist).
 *
 * Przechowuje konfigurację alertów dla konkretnego urządzenia.
 */
@Entity(
    tableName = "watchlist",
    foreignKeys =
    [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["fingerprint"],
            childColumns = ["deviceFingerprint"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["deviceFingerprint"], unique = true)],
)
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Fingerprint urządzenia (FK do devices) */
    val deviceFingerprint: String,
    /** Typ alertu (ON_APPEAR, ON_DISAPPEAR, ALWAYS) */
    val alertType: AlertType = AlertType.ON_APPEAR,
    /** Priorytet powiadomienia (1 = niski, 5 = krytyczny) */
    val priorityLevel: Int = 3,
    /** Czy wywołać akcję Smart Home przy wykryciu */
    val triggerSmartHome: Boolean = false,
    /** URL do wywołania (Shelly/HTTP webhook) */
    val smartHomeUrl: String? = null,
    /** Timestamp dodania do watchlist */
    val addedAt: Long = System.currentTimeMillis(),
)
