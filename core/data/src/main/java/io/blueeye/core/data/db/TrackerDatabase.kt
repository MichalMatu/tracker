package io.blueeye.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.blueeye.core.data.db.dao.AlertEvidenceEventDao
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.db.dao.WatchlistDao
import io.blueeye.core.data.db.entity.AlertEvidenceEventEntity
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.FollowMeObservationEntity
import io.blueeye.core.data.db.entity.SignalSampleEntity

/**
 * Główna baza danych Room aplikacji Tracker.
 *
 * Zawiera tabele:
 * - devices: Wykryte urządzenia Bluetooth
 * - signal_samples: Próbki RSSI z lokalizacją GPS
 * - follow_me_observations: Historia decyzji Follow-Me
 * - alert_evidence_events: Historia alertów i ich dowodów
 * - watchlist: Konfiguracja alertów dla obserwowanych urządzeń
 */
@Database(
    entities =
    [
        DeviceEntity::class,
        SignalSampleEntity::class,
        FollowMeObservationEntity::class,
        AlertEvidenceEventEntity::class,
        io.blueeye.core.data.db.entity.WatchlistEntity::class,
    ],
    version = 22,
    exportSchema = false,
)
@TypeConverters(Converters::class, EvidenceConverters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    abstract fun deviceUpdateDao(): io.blueeye.core.data.db.dao.DeviceUpdateDao

    abstract fun deviceActionDao(): io.blueeye.core.data.db.dao.DeviceActionDao

    abstract fun deviceSearchDao(): io.blueeye.core.data.db.dao.DeviceSearchDao

    abstract fun signalSampleDao(): SignalSampleDao

    abstract fun followMeObservationDao(): FollowMeObservationDao

    abstract fun alertEvidenceEventDao(): AlertEvidenceEventDao

    abstract fun watchlistDao(): WatchlistDao

    companion object {
        const val DATABASE_NAME = "tracker_database"
    }
}
