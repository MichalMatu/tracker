package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.dao.AlertEvidenceEventDao
import io.blueeye.core.data.db.entity.AlertEvidenceEventEntity
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertEvidenceEventRecorderTest {
    private val dao = FakeAlertEvidenceEventDao()
    private val recorder = AlertEvidenceEventRecorder(dao)

    @Test
    fun `recordWatchlistReturn stores critical watchlist evidence`() = runTest {
        val evidence = evidence(
            source = EvidenceSource.WATCHLIST,
            confidence = DetectionConfidence.CRITICAL,
            timestamp = NOW,
            rawValue = FINGERPRINT,
        )

        recorder.recordWatchlistReturn(FINGERPRINT, MAC, evidence)

        assertEquals(1, dao.events.size)
        val event = dao.events.single()
        assertEquals(AlertEvidenceEventType.WATCHLIST_RETURN, event.eventType)
        assertEquals(FINGERPRINT, event.deviceFingerprint)
        assertEquals(MAC, event.observedMac)
        assertEquals(EvidenceSource.WATCHLIST, event.evidenceSource)
        assertEquals(DetectionConfidence.CRITICAL, event.confidence)
        assertEquals(1, dao.trimCalls)
        assertEquals(1, dao.deleteOldCalls)
    }

    @Test
    fun `recordPublicSafetySignal debounces identical short interval evidence`() = runTest {
        val first = evidence(timestamp = NOW, rawValue = "0025DF")
        val duplicate = evidence(timestamp = NOW + ONE_MINUTE, rawValue = "0025DF")

        recorder.recordPublicSafetySignal(FINGERPRINT, MAC, first)
        recorder.recordPublicSafetySignal(FINGERPRINT, MAC, duplicate)

        assertEquals(1, dao.events.size)
    }

    @Test
    fun `recordPublicSafetySignal stores changed evidence inside debounce window`() = runTest {
        val first = evidence(timestamp = NOW, rawValue = "0025DF")
        val changed = evidence(
            timestamp = NOW + ONE_MINUTE,
            source = EvidenceSource.SERVICE_UUID,
            rawValue = "fd8e",
            provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
        )

        recorder.recordPublicSafetySignal(FINGERPRINT, MAC, first)
        recorder.recordPublicSafetySignal(FINGERPRINT, MAC, changed)

        assertEquals(2, dao.events.size)
        assertEquals(EvidenceSource.SERVICE_UUID, dao.events.last().evidenceSource)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, dao.events.last().evidenceProvenance)
    }

    @Test
    fun `recordFollowMeAlert stores follow me alert evidence`() = runTest {
        val evidence = evidence(
            source = EvidenceSource.FOLLOW_ME_SCORE,
            confidence = DetectionConfidence.MEDIUM,
            timestamp = NOW,
            rawValue = "score=64",
            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
        )

        recorder.recordFollowMeAlert(FINGERPRINT, MAC, evidence)

        assertEquals(1, dao.events.size)
        val event = dao.events.single()
        assertEquals(AlertEvidenceEventType.FOLLOW_ME_ALERT, event.eventType)
        assertEquals(EvidenceSource.FOLLOW_ME_SCORE, event.evidenceSource)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, event.evidenceProvenance)
        assertEquals(DetectionConfidence.MEDIUM, event.confidence)
    }

    @Test
    fun `recordFollowMeAlert debounces changed score evidence inside cooldown`() = runTest {
        val first = evidence(
            source = EvidenceSource.FOLLOW_ME_SCORE,
            confidence = DetectionConfidence.MEDIUM,
            timestamp = NOW,
            rawValue = "score=64",
        )
        val changedScore = first.copy(
            timestamp = NOW + ONE_MINUTE,
            rawValue = "score=72",
        )

        recorder.recordFollowMeAlert(FINGERPRINT, MAC, first)
        recorder.recordFollowMeAlert(FINGERPRINT, MAC, changedScore)

        assertEquals(1, dao.events.size)
    }

    private fun evidence(
        source: EvidenceSource = EvidenceSource.OUI,
        confidence: DetectionConfidence = DetectionConfidence.HIGH,
        timestamp: Long,
        rawValue: String?,
        provenance: EvidenceProvenance = EvidenceProvenance.UNKNOWN,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = "Evidence reason",
            timestamp = timestamp,
            rawValue = rawValue,
            parsedValue = "BODY_CAMERA",
            isPassive = true,
            provenance = provenance,
        )

    private class FakeAlertEvidenceEventDao : AlertEvidenceEventDao {
        val events = mutableListOf<AlertEvidenceEventEntity>()
        var trimCalls = 0
        var deleteOldCalls = 0

        override suspend fun insert(event: AlertEvidenceEventEntity) {
            events += event
        }

        override fun getRecentForDevice(
            fingerprint: String,
            limit: Int,
        ): Flow<List<AlertEvidenceEventEntity>> =
            flowOf(events.filter { it.deviceFingerprint == fingerprint }.take(limit))

        override fun getRecent(limit: Int): Flow<List<AlertEvidenceEventEntity>> =
            flowOf(events.take(limit))

        override suspend fun getLatestForDeviceEvent(
            fingerprint: String,
            eventType: AlertEvidenceEventType,
        ): AlertEvidenceEventEntity? =
            events
                .filter { it.deviceFingerprint == fingerprint && it.eventType == eventType }
                .maxByOrNull { it.timestamp }

        override suspend fun deleteOldEvents(beforeTimestamp: Long): Int {
            deleteOldCalls += 1
            return 0
        }

        override suspend fun trimDeviceHistory(
            fingerprint: String,
            keepCount: Int,
        ): Int {
            trimCalls += 1
            return 0
        }

        override suspend fun deleteAll() {
            events.clear()
        }

        override suspend fun deleteOrphanedEvents() = Unit
    }

    private companion object {
        private const val FINGERPRINT = "AA:BB:CC:11:22:33"
        private const val MAC = "AA:BB:CC:11:22:33"
        private const val NOW = 1_789_000_000_000L
        private const val ONE_MINUTE = 60_000L
    }
}
