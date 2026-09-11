from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


def write_new(path: Path, content: str) -> None:
    if path.exists():
        raise SystemExit(f"refusing to overwrite existing file: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)


write_new(
    Path("core/model/src/main/kotlin/io/blueeye/core/model/IdentityContinuityCandidate.kt"),
    '''package io.blueeye.core.model

/** Review-only identity continuity relation; it never implies an automatic merge. */
data class IdentityContinuityCandidate(
    val id: Long,
    val deviceFingerprint: String,
    val candidateFingerprint: String,
    val timestamp: Long,
    val reasonCode: String,
    val confidence: Float,
    val featureSummary: String,
    val verdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
)
''',
)

write_new(
    Path("core/data/src/main/java/io/blueeye/core/data/db/entity/IdentityContinuityCandidateEntity.kt"),
    '''package io.blueeye.core.data.db.entity

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
''',
)

write_new(
    Path("core/data/src/main/java/io/blueeye/core/data/db/dao/IdentityContinuityCandidateDao.kt"),
    '''package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
import io.blueeye.core.model.IdentityCarryoverVerdict

@Dao
interface IdentityContinuityCandidateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(candidate: IdentityContinuityCandidateEntity): Long

    @Query(
        """
        SELECT * FROM identity_continuity_candidates
        WHERE timestamp >= :sinceTimestamp
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getSince(sinceTimestamp: Long): List<IdentityContinuityCandidateEntity>

    @Query(
        "UPDATE identity_continuity_candidates SET verdict = :verdict WHERE id = :id",
    )
    suspend fun setVerdict(id: Long, verdict: IdentityCarryoverVerdict)

    @Query("DELETE FROM identity_continuity_candidates")
    suspend fun deleteAll()
}
''',
)

write_new(
    Path("core/data/src/main/java/io/blueeye/core/data/mapper/IdentityContinuityCandidateMapper.kt"),
    '''package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
import io.blueeye.core.model.IdentityContinuityCandidate

internal fun IdentityContinuityCandidateEntity.toDomain(): IdentityContinuityCandidate =
    IdentityContinuityCandidate(
        id = id,
        deviceFingerprint = deviceFingerprint,
        candidateFingerprint = candidateFingerprint,
        timestamp = timestamp,
        reasonCode = reasonCode,
        confidence = confidence,
        featureSummary = featureSummary,
        verdict = verdict,
    )

internal fun List<IdentityContinuityCandidateEntity>.toIdentityContinuityCandidateDomain():
    List<IdentityContinuityCandidate> = map(IdentityContinuityCandidateEntity::toDomain)
''',
)

ctx = Path("core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/ScanDataContext.kt")
replace_once(
    ctx,
    '''import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SensorData
''',
    '''import io.blueeye.core.data.tracker.model.IdentityCandidateMatch
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SensorData
''',
)
replace_once(
    ctx,
    '''    /** Compact feature summary used by the MAC carryover matcher */
    var carryoverFeatures: String? = null,

    /** Whether this scan result is provisional (too weak to persist) */
''',
    '''    /** Compact feature summary used by the MAC carryover matcher */
    var carryoverFeatures: String? = null,

    /** Review-only continuity candidate; never changes the current fingerprint by itself. */
    var identityCandidate: IdentityCandidateMatch? = null,

    /** Whether this scan result is provisional (too weak to persist) */
''',
)

resolver = Path("core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/MacAddressResolver.kt")
replace_once(
    resolver,
    '''        ctx.macChangeCount = correlationInfo.macChangeCount

        val stableMac = correlationInfo.correlatedMac
''',
    '''        ctx.macChangeCount = correlationInfo.macChangeCount
        ctx.identityCandidate = correlationInfo.identityCandidate

        val stableMac = correlationInfo.correlatedMac
''',
)

persister = Path("core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/DevicePersister.kt")
replace_once(
    persister,
    '''import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.entity.DeviceEntity
''',
    '''import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
''',
)
replace_once(
    persister,
    '''    private val priorityHelper: DeviceTypePriorityHelper,
    private val signalSamplePersister: SignalSamplePersister,
) {
''',
    '''    private val priorityHelper: DeviceTypePriorityHelper,
    private val signalSamplePersister: SignalSamplePersister,
    private val identityContinuityCandidateDao: IdentityContinuityCandidateDao,
) {
''',
)
replace_once(
    persister,
    '''        if (stageOutcome.shouldRecordFollowMeObservation) {
            followMeObservationRecorder.record(ctx)
        }

        val signalSampleOutcome = signalSamplePersister.persist(ctx, classifier)
''',
    '''        if (stageOutcome.shouldRecordFollowMeObservation) {
            followMeObservationRecorder.record(ctx)
        }
        persistIdentityCandidate(ctx)

        val signalSampleOutcome = signalSamplePersister.persist(ctx, classifier)
''',
)
replace_once(
    persister,
    '''    private suspend fun persistExistingDevice(
''',
    '''    private suspend fun persistIdentityCandidate(ctx: ScanDataContext) {
        val candidate = ctx.identityCandidate ?: return
        if (ctx.fingerprint != ctx.mac || candidate.candidateFingerprint == ctx.fingerprint) return
        identityContinuityCandidateDao.insert(
            IdentityContinuityCandidateEntity(
                deviceFingerprint = ctx.fingerprint,
                candidateFingerprint = candidate.candidateFingerprint,
                timestamp = ctx.timestamp,
                reasonCode = candidate.evidence.reasonCode.name,
                confidence = candidate.evidence.confidence,
                featureSummary = candidate.evidence.featureSummary,
            ),
        )
    }

    private suspend fun persistExistingDevice(
''',
)

persister_test = Path("core/data/src/test/kotlin/io/blueeye/core/data/repository/handler/ble/DevicePersisterDebounceTest.kt")
replace_once(
    persister_test,
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
)
replace_once(
    persister_test,
    '''    private val followMeObservationDao: FollowMeObservationDao = mock()
    private val followMeObservationRecorder = FollowMeObservationRecorder(followMeObservationDao)
''',
    '''    private val followMeObservationDao: FollowMeObservationDao = mock()
    private val identityContinuityCandidateDao: IdentityContinuityCandidateDao = mock()
    private val followMeObservationRecorder = FollowMeObservationRecorder(followMeObservationDao)
''',
)
replace_once(
    persister_test,
    '''        priorityHelper = priorityHelper,
        signalSamplePersister = signalSamplePersister,
    )
''',
    '''        priorityHelper = priorityHelper,
        signalSamplePersister = signalSamplePersister,
        identityContinuityCandidateDao = identityContinuityCandidateDao,
    )
''',
)

history = Path("core/data/src/main/java/io/blueeye/core/data/repository/DeviceHistoryDataSource.kt")
replace_once(
    history,
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
)
replace_once(
    history,
    '''import io.blueeye.core.data.mapper.toFollowMeHistoryDomain
import io.blueeye.core.model.AlertEvidenceEvent
''',
    '''import io.blueeye.core.data.mapper.toFollowMeHistoryDomain
import io.blueeye.core.data.mapper.toIdentityContinuityCandidateDomain
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.IdentityContinuityCandidate
''',
)
replace_once(
    history,
    '''    private val followMeObservationDao: FollowMeObservationDao,
    private val alertEvidenceEventDao: AlertEvidenceEventDao,
) {
''',
    '''    private val followMeObservationDao: FollowMeObservationDao,
    private val alertEvidenceEventDao: AlertEvidenceEventDao,
    private val identityContinuityCandidateDao: IdentityContinuityCandidateDao,
) {
''',
)
replace_once(
    history,
    '''    fun getRecentAlertEvidenceEvents(): Flow<List<AlertEvidenceEvent>> =
        alertEvidenceEventDao.getRecent()
            .map { entities -> entities.toAlertEvidenceEventDomain() }

    suspend fun deleteOrphanedHistory() {
''',
    '''    fun getRecentAlertEvidenceEvents(): Flow<List<AlertEvidenceEvent>> =
        alertEvidenceEventDao.getRecent()
            .map { entities -> entities.toAlertEvidenceEventDomain() }

    suspend fun getIdentityCandidatesSince(sinceTimestamp: Long): List<IdentityContinuityCandidate> =
        identityContinuityCandidateDao.getSince(sinceTimestamp).toIdentityContinuityCandidateDomain()

    suspend fun deleteOrphanedHistory() {
''',
)
replace_once(
    history,
    '''        alertEvidenceEventDao.deleteAll()
    }
}
''',
    '''        alertEvidenceEventDao.deleteAll()
        identityContinuityCandidateDao.deleteAll()
    }
}
''',
)

domain_repo = Path("core/domain/src/main/java/io/blueeye/core/domain/repository/DeviceSplitRepositories.kt")
replace_once(
    domain_repo,
    '''import io.blueeye.core.model.IdentityCarryoverVerdict
import kotlinx.coroutines.flow.Flow
''',
    '''import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import kotlinx.coroutines.flow.Flow
''',
)
replace_once(
    domain_repo,
    '''interface DeviceAlertEvidenceRepository {
    fun getAlertEvidenceEvents(fingerprint: String): Flow<Result<List<AlertEvidenceEvent>>>

    fun getRecentAlertEvidenceEvents(): Flow<Result<List<AlertEvidenceEvent>>>
}
''',
    '''interface DeviceAlertEvidenceRepository {
    fun getAlertEvidenceEvents(fingerprint: String): Flow<Result<List<AlertEvidenceEvent>>>

    fun getRecentAlertEvidenceEvents(): Flow<Result<List<AlertEvidenceEvent>>>

    suspend fun getIdentityCandidatesSince(sinceTimestamp: Long): Result<List<IdentityContinuityCandidate>>
}
''',
)

repo = Path("core/data/src/main/java/io/blueeye/core/data/repository/DeviceRepositoryImpl.kt")
replace_once(
    repo,
    '''    override fun getRecentAlertEvidenceEvents(): Flow<Result<List<io.blueeye.core.model.AlertEvidenceEvent>>> {
        return deviceHistoryDataSource.getRecentAlertEvidenceEvents()
            .asResult()
    }

    override suspend fun updateDeviceConfig(
''',
    '''    override fun getRecentAlertEvidenceEvents(): Flow<Result<List<io.blueeye.core.model.AlertEvidenceEvent>>> {
        return deviceHistoryDataSource.getRecentAlertEvidenceEvents()
            .asResult()
    }

    override suspend fun getIdentityCandidatesSince(
        sinceTimestamp: Long,
    ): Result<List<io.blueeye.core.model.IdentityContinuityCandidate>> = runCatching {
        deviceHistoryDataSource.getIdentityCandidatesSince(sinceTimestamp)
    }

    override suspend fun updateDeviceConfig(
''',
)

tracker_db = Path("core/data/src/main/java/io/blueeye/core/data/db/TrackerDatabase.kt")
replace_once(
    tracker_db,
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
)
replace_once(
    tracker_db,
    '''import io.blueeye.core.data.db.entity.FollowMeObservationEntity
import io.blueeye.core.data.db.entity.SignalSampleEntity
''',
    '''import io.blueeye.core.data.db.entity.FollowMeObservationEntity
import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
import io.blueeye.core.data.db.entity.SignalSampleEntity
''',
)
replace_once(
    tracker_db,
    '''        AlertEvidenceEventEntity::class,
        io.blueeye.core.data.db.entity.WatchlistEntity::class,
    ],
    version = 22,
''',
    '''        AlertEvidenceEventEntity::class,
        IdentityContinuityCandidateEntity::class,
        io.blueeye.core.data.db.entity.WatchlistEntity::class,
    ],
    version = 23,
''',
)
replace_once(
    tracker_db,
    '''    abstract fun alertEvidenceEventDao(): AlertEvidenceEventDao

    abstract fun watchlistDao(): WatchlistDao
''',
    '''    abstract fun alertEvidenceEventDao(): AlertEvidenceEventDao

    abstract fun identityContinuityCandidateDao(): IdentityContinuityCandidateDao

    abstract fun watchlistDao(): WatchlistDao
''',
)

module = Path("core/data/src/main/java/io/blueeye/core/di/DatabaseModule.kt")
replace_once(
    module,
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
    '''import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.dao.SignalSampleDao
''',
)
replace_once(
    module,
    '''            .addMigrations(migration19To20, migration20To21, migration21To22)
            .fallbackToDestructiveMigration() // Na etapie developmentu
''',
    '''            .addMigrations(migration19To20, migration20To21, migration21To22, migration22To23)
            .fallbackToDestructiveMigration() // Na etapie developmentu
''',
)
replace_once(
    module,
    '''    fun provideAlertEvidenceEventDao(database: TrackerDatabase): AlertEvidenceEventDao {
        return database.alertEvidenceEventDao()
    }

    @Provides
    @Singleton
    fun provideWatchlistDao(database: TrackerDatabase): WatchlistDao {
''',
    '''    fun provideAlertEvidenceEventDao(database: TrackerDatabase): AlertEvidenceEventDao {
        return database.alertEvidenceEventDao()
    }

    @Provides
    @Singleton
    fun provideIdentityContinuityCandidateDao(database: TrackerDatabase): IdentityContinuityCandidateDao {
        return database.identityContinuityCandidateDao()
    }

    @Provides
    @Singleton
    fun provideWatchlistDao(database: TrackerDatabase): WatchlistDao {
''',
)
replace_once(
    module,
    '''    private const val DATABASE_VERSION_22 = 22

    private val migration13To14 =
''',
    '''    private const val DATABASE_VERSION_22 = 22
    private const val DATABASE_VERSION_23 = 23

    private val migration13To14 =
''',
)
replace_once(
    module,
    '''    private val migration21To22 =
        object : Migration(DATABASE_VERSION_21, DATABASE_VERSION_22) {
            override fun migrate(db: SupportSQLiteDatabase) {
''',
    '''    private val migration21To22 =
        object : Migration(DATABASE_VERSION_21, DATABASE_VERSION_22) {
            override fun migrate(db: SupportSQLiteDatabase) {
''',
)
# Append migration before object closing.
replace_once(
    module,
    '''                db.execSQL("ALTER TABLE signal_samples ADD COLUMN followingScore REAL")
            }
        }
}
''',
    '''                db.execSQL("ALTER TABLE signal_samples ADD COLUMN followingScore REAL")
            }
        }

    private val migration22To23 =
        object : Migration(DATABASE_VERSION_22, DATABASE_VERSION_23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS identity_continuity_candidates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceFingerprint TEXT NOT NULL,
                        candidateFingerprint TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        reasonCode TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        featureSummary TEXT NOT NULL,
                        verdict TEXT NOT NULL DEFAULT 'UNREVIEWED',
                        FOREIGN KEY(deviceFingerprint) REFERENCES devices(fingerprint) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_identity_continuity_candidates_deviceFingerprint " +
                        "ON identity_continuity_candidates(deviceFingerprint)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_identity_continuity_candidates_candidateFingerprint " +
                        "ON identity_continuity_candidates(candidateFingerprint)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_identity_continuity_candidates_timestamp " +
                        "ON identity_continuity_candidates(timestamp)",
                )
            }
        }
}
''',
)

action_dao = Path("core/data/src/main/java/io/blueeye/core/data/db/dao/DeviceActionDao.kt")
replace_once(
    action_dao,
    '''    @Query("DELETE FROM devices WHERE fingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)
''',
    '''    @Query(
        "UPDATE identity_continuity_candidates SET deviceFingerprint = :targetFingerprint " +
            "WHERE deviceFingerprint = :sourceFingerprint",
    )
    suspend fun moveIdentityCandidates(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query(
        "UPDATE identity_continuity_candidates SET candidateFingerprint = :targetFingerprint " +
            "WHERE candidateFingerprint = :sourceFingerprint",
    )
    suspend fun retargetIdentityCandidates(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query("DELETE FROM devices WHERE fingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)
''',
)
replace_once(
    action_dao,
    '''        moveAlertEvidenceEvents(targetFingerprint, duplicateFingerprint)
        deleteByFingerprint(duplicateFingerprint)
''',
    '''        moveAlertEvidenceEvents(targetFingerprint, duplicateFingerprint)
        moveIdentityCandidates(targetFingerprint, duplicateFingerprint)
        retargetIdentityCandidates(targetFingerprint, duplicateFingerprint)
        deleteByFingerprint(duplicateFingerprint)
''',
)

exporter = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
replace_once(
    exporter,
    '''import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.SignalSample
''',
    '''import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
''',
)
replace_once(
    exporter,
    '''            val sessionAlertEvidenceEvents =
                loadSessionAlertEvidenceEvents(
                    devices = sessionDevices,
                    sessionStartedAt = sessionStartedAt,
                )

            return DatabaseExportData(
''',
    '''            val sessionAlertEvidenceEvents =
                loadSessionAlertEvidenceEvents(
                    devices = sessionDevices,
                    sessionStartedAt = sessionStartedAt,
                )
            val sessionIdentityCandidates =
                runCatching {
                    deviceRepository.getIdentityCandidatesSince(sessionStartedAt)
                        .getOrDefault(emptyList())
                }.getOrDefault(emptyList())

            return DatabaseExportData(
''',
)
replace_once(
    exporter,
    '''                        followMeObservations = sessionFollowMeObservations,
                        alertEvidenceEvents = sessionAlertEvidenceEvents,
                    ),
''',
    '''                        followMeObservations = sessionFollowMeObservations,
                        alertEvidenceEvents = sessionAlertEvidenceEvents,
                        identityCandidates = sessionIdentityCandidates,
                    ),
''',
)
replace_once(
    exporter,
    '''    val followMeObservations: List<SessionFollowMeObservation>,
    val alertEvidenceEvents: List<AlertEvidenceEvent>,
)
''',
    '''    val followMeObservations: List<SessionFollowMeObservation>,
    val alertEvidenceEvents: List<AlertEvidenceEvent>,
    val identityCandidates: List<IdentityContinuityCandidate> = emptyList(),
)
''',
)
replace_once(
    exporter,
    '''            put("alertEvidenceEventCount", session.alertEvidenceEvents.size)
            put("activeProbeDataDeviceCount", activeProbeSummary.dataDeviceCount)
''',
    '''            put("alertEvidenceEventCount", session.alertEvidenceEvents.size)
            put("identityCandidateCount", session.identityCandidates.size)
            put("activeProbeDataDeviceCount", activeProbeSummary.dataDeviceCount)
''',
)
replace_once(
    exporter,
    '''            put(
                "alertEvidenceEvents",
                JsonArray(
                    session.alertEvidenceEvents.map(
                        SessionHistoryExportJsonMapper::mapAlertEvidenceEvent,
                    ),
                ),
            )
            put(
                "decodedSignals",
''',
    '''            put(
                "alertEvidenceEvents",
                JsonArray(
                    session.alertEvidenceEvents.map(
                        SessionHistoryExportJsonMapper::mapAlertEvidenceEvent,
                    ),
                ),
            )
            put(
                "identityContinuityCandidates",
                JsonArray(session.identityCandidates.map(::mapIdentityCandidate)),
            )
            put(
                "decodedSignals",
''',
)
replace_once(
    exporter,
    '''    private fun JsonObjectBuilder.putSampleQuality(samples: List<SignalSample>) {
''',
    '''    private fun mapIdentityCandidate(candidate: IdentityContinuityCandidate): JsonObject =
        buildJsonObject {
            put("id", candidate.id)
            put("deviceFingerprint", candidate.deviceFingerprint)
            put("candidateFingerprint", candidate.candidateFingerprint)
            put("timestamp", candidate.timestamp)
            put("reasonCode", candidate.reasonCode)
            put("confidence", candidate.confidence)
            put("featureSummary", candidate.featureSummary)
            put("verdict", candidate.verdict.name)
        }

    private fun JsonObjectBuilder.putSampleQuality(samples: List<SignalSample>) {
''',
)
replace_once(exporter, '    private const val SCHEMA_VERSION = 19\n', '    private const val SCHEMA_VERSION = 20\n')
