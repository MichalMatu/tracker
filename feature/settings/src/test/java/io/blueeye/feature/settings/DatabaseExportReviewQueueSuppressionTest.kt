package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseExportReviewQueueSuppressionTest {
    @Test
    fun `export does not queue user suppressed follow me devices for session review`() {
        val falsePositiveDevice = falsePositiveFollowMeDevice()
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(falsePositiveDevice),
                    samples = emptyList(),
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(falsePositiveDevice),
                            samples = emptyList(),
                            label = DeviceCalibrationLabel.FALSE_POSITIVE,
                            startedAt = SESSION_STARTED_AT,
                            notes = "Home baseline near known headphones.",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = listOf(followMeAlertEvent(falsePositiveDevice)),
                        ),
                    exportDate = SESSION_STARTED_AT + 120_000L,
                ),
            )
        val session = export.getValue("session").jsonObject
        val deviceSummary = session.getValue("deviceSummaries").jsonArray.single().jsonObject

        assertEquals(1, session.getValue("alertEvidenceEventCount").jsonPrimitive.int)
        assertTrue(session.getValue("reviewDeviceQueue").jsonArray.isEmpty())
        assertEquals("UNKNOWN_NOISE", deviceSummary.getValue("reviewCategory").jsonPrimitive.content)
        assertEquals(
            "Already suppressed by user calibration; change the verdict only if new evidence appears.",
            deviceSummary.getValue("reviewAction").jsonPrimitive.content,
        )
    }

    private fun falsePositiveFollowMeDevice(): Device =
        Device(
            fingerprint = "false-positive-follow-me",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device false-positive-follow-me",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Unknown Vendor",
            predictedModel = null,
            trackingStatus = TrackingStatus.SUSPICIOUS,
            followingScore = 91f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            isIgnoredForTracking = true,
            firstSeenAt = SESSION_STARTED_AT - 60_000L,
            lastSeenAt = SESSION_STARTED_AT + 15_000L,
            rssi = -55,
            encounterCount = 1,
            calibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
            evidence = listOf(followMeEvidence()),
        )

    private fun followMeAlertEvent(device: Device): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = SESSION_STARTED_AT + 21_000L,
            deviceFingerprint = device.fingerprint,
            observedMac = device.macAddress,
            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
            evidence = followMeEvidence(),
        )

    private fun followMeEvidence(): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.FOLLOW_ME_SCORE,
            confidence = DetectionConfidence.HIGH,
            reasonText = "Historical Follow-Me alert was later marked false positive.",
            timestamp = SESSION_STARTED_AT + 10_000L,
            rawValue = "91",
            parsedValue = TrackingStatus.SUSPICIOUS.name,
            isPassive = true,
        )

    private companion object {
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
    }
}
