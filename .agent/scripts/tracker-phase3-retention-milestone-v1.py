from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


action_dao = Path("core/data/src/main/java/io/blueeye/core/data/db/dao/DeviceActionDao.kt")
replace_once(
    action_dao,
    '''    @Query("DELETE FROM devices WHERE lastSeenAt < :beforeTimestamp AND isInWatchlist = 0")
    suspend fun deleteOldDevices(beforeTimestamp: Long): Int
''',
    '''    @Query(
        """
        DELETE FROM devices
        WHERE lastSeenAt < :beforeTimestamp
            AND isInWatchlist = 0
            AND isSafeBeacon = 0
            AND (userAlias IS NULL OR TRIM(userAlias) = '')
            AND (userNotes IS NULL OR TRIM(userNotes) = '')
            AND alertSound = 0
            AND alertVibration = 0
            AND isTrackingEnabled = 1
            AND isIgnoredForTracking = 0
            AND calibrationLabel = 'UNKNOWN'
            AND identityCarryoverVerdict = 'UNREVIEWED'
            AND fingerprint NOT IN (SELECT deviceFingerprint FROM watchlist)
            AND fingerprint NOT IN (SELECT deviceFingerprint FROM identity_continuity_candidates)
            AND fingerprint NOT IN (SELECT candidateFingerprint FROM identity_continuity_candidates)
        """,
    )
    suspend fun deleteOldDevices(beforeTimestamp: Long): Int
''',
)

candidate_dao = Path("core/data/src/main/java/io/blueeye/core/data/db/dao/IdentityContinuityCandidateDao.kt")
replace_once(
    candidate_dao,
    '''    @Query("DELETE FROM identity_continuity_candidates")
    suspend fun deleteAll()
''',
    '''    @Query("DELETE FROM identity_continuity_candidates WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldCandidates(beforeTimestamp: Long): Int

    @Query("DELETE FROM identity_continuity_candidates")
    suspend fun deleteAll()
''',
)

history = Path("core/data/src/main/java/io/blueeye/core/data/repository/DeviceHistoryDataSource.kt")
replace_once(
    history,
    '''    suspend fun deleteOrphanedHistory() {
''',
    '''    suspend fun deleteExpiredHistory(now: Long) {
        signalSampleDao.deleteOldSamples(now - SIGNAL_SAMPLE_RETENTION_MS)
        val evidenceBefore = now - EVIDENCE_RETENTION_MS
        followMeObservationDao.deleteOldObservations(evidenceBefore)
        alertEvidenceEventDao.deleteOldEvents(evidenceBefore)
        identityContinuityCandidateDao.deleteOldCandidates(evidenceBefore)
    }

    suspend fun deleteOrphanedHistory() {
''',
)
replace_once(
    history,
    '''        identityContinuityCandidateDao.deleteAll()
    }
}
''',
    '''        identityContinuityCandidateDao.deleteAll()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val SIGNAL_SAMPLE_RETENTION_MS = 7L * DAY_MS
        const val EVIDENCE_RETENTION_MS = 30L * DAY_MS
    }
}
''',
)

repo = Path("core/data/src/main/java/io/blueeye/core/data/repository/DeviceRepositoryImpl.kt")
replace_once(
    repo,
    '''    override suspend fun deleteOldDevices(maxAgeMs: Long): Result<Int> = runCatching {
        val timestamp = System.currentTimeMillis() - maxAgeMs
        deviceDao.deleteOldDevices(timestamp)
    }
''',
    '''    override suspend fun deleteOldDevices(maxAgeMs: Long): Result<Int> = runCatching {
        val now = System.currentTimeMillis()
        deviceHistoryDataSource.deleteExpiredHistory(now)
        deviceDao.deleteOldDevices(now - maxAgeMs)
    }
''',
)
