package io.blueeye.core.data.evidence

import android.util.Log
import io.blueeye.core.data.db.dao.AlertEvidenceEventDao
import io.blueeye.core.data.db.entity.AlertEvidenceEventEntity
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionEvidence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertEvidenceEventRecorder @Inject constructor(
    private val alertEvidenceEventDao: AlertEvidenceEventDao,
) {
    suspend fun recordWatchlistReturn(
        deviceFingerprint: String,
        observedMac: String,
        evidence: DetectionEvidence,
    ) {
        record(
            deviceFingerprint = deviceFingerprint,
            observedMac = observedMac,
            eventType = AlertEvidenceEventType.WATCHLIST_RETURN,
            evidence = evidence,
        )
    }

    suspend fun recordPublicSafetySignal(
        deviceFingerprint: String,
        observedMac: String,
        evidence: DetectionEvidence,
    ) {
        record(
            deviceFingerprint = deviceFingerprint,
            observedMac = observedMac,
            eventType = AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL,
            evidence = evidence,
        )
    }

    suspend fun recordFollowMeAlert(
        deviceFingerprint: String,
        observedMac: String,
        evidence: DetectionEvidence,
    ) {
        record(
            deviceFingerprint = deviceFingerprint,
            observedMac = observedMac,
            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
            evidence = evidence,
        )
    }

    private suspend fun record(
        deviceFingerprint: String,
        observedMac: String,
        eventType: AlertEvidenceEventType,
        evidence: DetectionEvidence,
    ) {
        try {
            val latest = alertEvidenceEventDao.getLatestForDeviceEvent(deviceFingerprint, eventType)
            if (!shouldRecord(latest, eventType, evidence)) return

            alertEvidenceEventDao.insert(
                AlertEvidenceEventEntity(
                    deviceFingerprint = deviceFingerprint,
                    observedMac = observedMac,
                    eventType = eventType,
                    timestamp = evidence.timestamp,
                    evidenceSource = evidence.source,
                    confidence = evidence.confidence,
                    reasonText = evidence.reasonText,
                    rawValue = evidence.rawValue,
                    parsedValue = evidence.parsedValue,
                    isPassive = evidence.isPassive,
                    evidenceProvenance = evidence.provenance,
                ),
            )
            alertEvidenceEventDao.trimDeviceHistory(deviceFingerprint, ALERT_EVENT_HISTORY_LIMIT)
            alertEvidenceEventDao.deleteOldEvents(evidence.timestamp - ALERT_EVENT_RETENTION_MS)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Alert evidence event insert failed: ${e.message}")
        }
    }

    private fun shouldRecord(
        latest: AlertEvidenceEventEntity?,
        eventType: AlertEvidenceEventType,
        evidence: DetectionEvidence,
    ): Boolean =
        latest == null ||
            evidence.timestamp - latest.timestamp >= eventType.debounceMs ||
            (
                eventType.recordsChangedEvidenceWithinDebounce &&
                    (
                        latest.evidenceSource != evidence.source ||
                            latest.confidence != evidence.confidence ||
                            latest.evidenceProvenance != evidence.provenance ||
                            latest.rawValue != evidence.rawValue ||
                            latest.parsedValue != evidence.parsedValue
                    )
            )

    private val AlertEvidenceEventType.debounceMs: Long
        get() =
            when (this) {
                AlertEvidenceEventType.WATCHLIST_RETURN -> WATCHLIST_EVENT_DEBOUNCE_MS
                AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL -> PUBLIC_SAFETY_EVENT_DEBOUNCE_MS
                AlertEvidenceEventType.FOLLOW_ME_ALERT -> FOLLOW_ME_EVENT_DEBOUNCE_MS
            }

    private val AlertEvidenceEventType.recordsChangedEvidenceWithinDebounce: Boolean
        get() =
            when (this) {
                AlertEvidenceEventType.WATCHLIST_RETURN,
                AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL,
                -> true
                AlertEvidenceEventType.FOLLOW_ME_ALERT -> false
            }

    private companion object {
        private const val TAG = "AlertEvidenceEventRecorder"
        private const val ALERT_EVENT_HISTORY_LIMIT = 200
        private const val ALERT_EVENT_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private const val PUBLIC_SAFETY_EVENT_DEBOUNCE_MS = 5L * 60L * 1000L
        private const val FOLLOW_ME_EVENT_DEBOUNCE_MS = 5L * 60L * 1000L
        private const val WATCHLIST_EVENT_DEBOUNCE_MS = 1L
    }
}
