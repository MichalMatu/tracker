package io.blueeye.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.blueeye.core.data.db.TrackerDatabase
import io.blueeye.core.data.db.dao.AlertEvidenceEventDao
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.db.dao.WatchlistDao
import javax.inject.Singleton

/** Moduł Hilt dostarczający instancje bazy danych i DAO. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TrackerDatabase {
        return Room.databaseBuilder(
            context,
            TrackerDatabase::class.java,
            TrackerDatabase.DATABASE_NAME,
        )
            .addMigrations(migration13To14, migration14To15)
            .addMigrations(migration15To16, migration16To17, migration17To18, migration18To19)
            .addMigrations(migration19To20, migration20To21, migration21To22)
            .fallbackToDestructiveMigration() // Na etapie developmentu
            .build()
    }

    @Provides
    @Singleton
    fun provideDeviceDao(database: TrackerDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    @Singleton
    fun provideSignalSampleDao(database: TrackerDatabase): SignalSampleDao {
        return database.signalSampleDao()
    }

    @Provides
    @Singleton
    fun provideFollowMeObservationDao(database: TrackerDatabase): FollowMeObservationDao {
        return database.followMeObservationDao()
    }

    @Provides
    @Singleton
    fun provideAlertEvidenceEventDao(database: TrackerDatabase): AlertEvidenceEventDao {
        return database.alertEvidenceEventDao()
    }

    @Provides
    @Singleton
    fun provideWatchlistDao(database: TrackerDatabase): WatchlistDao {
        return database.watchlistDao()
    }

    private const val DATABASE_VERSION_13 = 13
    private const val DATABASE_VERSION_14 = 14
    private const val DATABASE_VERSION_15 = 15
    private const val DATABASE_VERSION_16 = 16
    private const val DATABASE_VERSION_17 = 17
    private const val DATABASE_VERSION_18 = 18
    private const val DATABASE_VERSION_19 = 19
    private const val DATABASE_VERSION_20 = 20
    private const val DATABASE_VERSION_21 = 21
    private const val DATABASE_VERSION_22 = 22

    private val migration13To14 =
        object : Migration(DATABASE_VERSION_13, DATABASE_VERSION_14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE devices ADD COLUMN calibrationLabel TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
            }
        }

    private val migration14To15 =
        object : Migration(DATABASE_VERSION_14, DATABASE_VERSION_15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE devices
                    SET lastRssi = -100
                    WHERE lastRssi = -50
                        AND technology = 'CLASSIC'
                        AND classOfDevice IS NULL
                        AND connectionStatus IN ('PROBING', 'RFCOMM_FAIL')
                    """.trimIndent(),
                )
            }
        }

    private val migration15To16 =
        object : Migration(DATABASE_VERSION_15, DATABASE_VERSION_16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeDurationScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeRssiStabilityScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeDeviceTypeScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeMacBehaviorScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeEncounterScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeUserMoved INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN followMeBaselineDevice INTEGER")
            }
        }

    private val migration16To17 =
        object : Migration(DATABASE_VERSION_16, DATABASE_VERSION_17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS follow_me_observations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceFingerprint TEXT NOT NULL,
                        observedMac TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        trackingStatus TEXT NOT NULL,
                        score REAL NOT NULL,
                        explanation TEXT,
                        rssi INTEGER NOT NULL,
                        encounterCount INTEGER NOT NULL,
                        durationScore INTEGER NOT NULL,
                        rssiStabilityScore INTEGER NOT NULL,
                        deviceTypeScore INTEGER NOT NULL,
                        macBehaviorScore INTEGER NOT NULL,
                        encounterScore INTEGER NOT NULL,
                        userMoved INTEGER,
                        baselineDevice INTEGER,
                        FOREIGN KEY(deviceFingerprint) REFERENCES devices(fingerprint) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_follow_me_observations_deviceFingerprint " +
                        "ON follow_me_observations(deviceFingerprint)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_follow_me_observations_timestamp " +
                        "ON follow_me_observations(timestamp)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_follow_me_observations_deviceFingerprint_timestamp " +
                        "ON follow_me_observations(deviceFingerprint, timestamp)",
                )
            }
        }

    private val migration17To18 =
        object : Migration(DATABASE_VERSION_17, DATABASE_VERSION_18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS alert_evidence_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceFingerprint TEXT NOT NULL,
                        observedMac TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        evidenceSource TEXT NOT NULL,
                        confidence TEXT NOT NULL,
                        reasonText TEXT NOT NULL,
                        rawValue TEXT,
                        parsedValue TEXT,
                        isPassive INTEGER NOT NULL,
                        FOREIGN KEY(deviceFingerprint) REFERENCES devices(fingerprint) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alert_evidence_events_deviceFingerprint " +
                        "ON alert_evidence_events(deviceFingerprint)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alert_evidence_events_timestamp " +
                        "ON alert_evidence_events(timestamp)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alert_evidence_events_deviceFingerprint_eventType_timestamp " +
                        "ON alert_evidence_events(deviceFingerprint, eventType, timestamp)",
                )
            }
        }

    private val migration18To19 =
        object : Migration(DATABASE_VERSION_18, DATABASE_VERSION_19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alert_evidence_events " +
                        "ADD COLUMN evidenceProvenance TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
            }
        }

    private val migration19To20 =
        object : Migration(DATABASE_VERSION_19, DATABASE_VERSION_20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN carryoverReasonCode TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN carryoverConfidence REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN carryoverFeatures TEXT")
            }
        }

    private val migration20To21 =
        object : Migration(DATABASE_VERSION_20, DATABASE_VERSION_21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE devices " +
                        "ADD COLUMN identityCarryoverVerdict TEXT NOT NULL DEFAULT 'UNREVIEWED'",
                )
            }
        }

    private val migration21To22 =
        object : Migration(DATABASE_VERSION_21, DATABASE_VERSION_22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val textColumns =
                    listOf(
                        "observedMac",
                        "technology",
                        "deviceName",
                        "deviceType",
                        "vendorName",
                        "manufacturerDataHex",
                        "manufacturerDataByIdHex",
                        "serviceUuids",
                        "serviceDataByUuidHex",
                        "beaconType",
                        "rawDataHex",
                        "sensorData",
                        "trackingStatus",
                        "tacticalCategory",
                        "probeError",
                    )
                textColumns.forEach { column ->
                    db.execSQL("ALTER TABLE signal_samples ADD COLUMN $column TEXT")
                }

                val integerColumns =
                    listOf(
                        "manufacturerId",
                        "appearance",
                        "txPower",
                        "isConnectable",
                        "primaryPhy",
                        "secondaryPhy",
                        "advertisingIntervalMs",
                        "classOfDevice",
                        "isTactical",
                    )
                integerColumns.forEach { column ->
                    db.execSQL("ALTER TABLE signal_samples ADD COLUMN $column INTEGER")
                }

                db.execSQL("ALTER TABLE signal_samples ADD COLUMN followingScore REAL")
            }
        }
}
