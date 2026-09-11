package io.blueeye.core.data.repository

import io.blueeye.core.data.db.dao.AlertEvidenceEventDao
import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.mapper.toAlertEvidenceEventDomain
import io.blueeye.core.data.mapper.toDomain
import io.blueeye.core.data.mapper.toFollowMeHistoryDomain
import io.blueeye.core.data.mapper.toIdentityContinuityCandidateDomain
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceHistoryDataSource @Inject constructor(
    private val signalSampleDao: SignalSampleDao,
    private val followMeObservationDao: FollowMeObservationDao,
    private val alertEvidenceEventDao: AlertEvidenceEventDao,
    private val identityContinuityCandidateDao: IdentityContinuityCandidateDao,
) {
    fun getSignalSamples(fingerprint: String): Flow<List<SignalSample>> =
        signalSampleDao.getSamplesForDevice(fingerprint)
            .map { entities -> entities.toDomain() }

    fun getFollowMeHistory(fingerprint: String): Flow<List<FollowMeHistorySample>> =
        followMeObservationDao.getRecentForDevice(fingerprint)
            .map { entities -> entities.toFollowMeHistoryDomain() }

    fun getAlertEvidenceEvents(fingerprint: String): Flow<List<AlertEvidenceEvent>> =
        alertEvidenceEventDao.getRecentForDevice(fingerprint)
            .map { entities -> entities.toAlertEvidenceEventDomain() }

    fun getRecentAlertEvidenceEvents(): Flow<List<AlertEvidenceEvent>> =
        alertEvidenceEventDao.getRecent()
            .map { entities -> entities.toAlertEvidenceEventDomain() }

    suspend fun getIdentityCandidatesSince(sinceTimestamp: Long): List<IdentityContinuityCandidate> =
        identityContinuityCandidateDao.getSince(sinceTimestamp).toIdentityContinuityCandidateDomain()

    suspend fun deleteExpiredHistory(now: Long) {
        signalSampleDao.deleteOldSamples(now - SIGNAL_SAMPLE_RETENTION_MS)
        val evidenceBefore = now - EVIDENCE_RETENTION_MS
        followMeObservationDao.deleteOldObservations(evidenceBefore)
        alertEvidenceEventDao.deleteOldEvents(evidenceBefore)
        identityContinuityCandidateDao.deleteOldCandidates(evidenceBefore)
    }

    suspend fun deleteOrphanedHistory() {
        signalSampleDao.deleteOrphanedSamples()
        followMeObservationDao.deleteOrphanedObservations()
        alertEvidenceEventDao.deleteOrphanedEvents()
    }

    suspend fun deleteAllHistory() {
        signalSampleDao.deleteAll()
        followMeObservationDao.deleteAll()
        alertEvidenceEventDao.deleteAll()
        identityContinuityCandidateDao.deleteAll()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val SIGNAL_SAMPLE_RETENTION_MS = 7L * DAY_MS
        const val EVIDENCE_RETENTION_MS = 30L * DAY_MS
    }
}
