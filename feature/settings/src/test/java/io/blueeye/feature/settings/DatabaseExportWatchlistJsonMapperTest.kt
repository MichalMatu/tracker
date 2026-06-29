package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DatabaseExportWatchlistJsonMapperTest {
    @Test
    fun `export includes watchlist tracking review decisions`() {
        val watchlistDevice =
            watchlistDevice(
                fingerprint = "watchlist-device",
                lastSeenAt = SESSION_STARTED_AT + 10_000L,
            )
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(watchlistDevice),
                    samples = emptyList(),
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(watchlistDevice),
                            samples = emptyList(),
                            label = DeviceCalibrationLabel.UNKNOWN,
                            startedAt = SESSION_STARTED_AT,
                            notes = "",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = listOf(watchlistReturnEvent(watchlistDevice)),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )

        val decisions =
            export.getValue("session").jsonObject
                .getValue("reviewDeviceQueue").jsonArray.single().jsonObject
                .getValue("decisions").jsonArray
        val pauseDecision = decisions[0].jsonObject

        assertEquals("WATCHLIST_TRACKING", pauseDecision.getValue("kind").jsonPrimitive.content)
        assertEquals("Pause alerts", pauseDecision.getValue("text").jsonPrimitive.content)
        assertFalse(pauseDecision.getValue("watchlistTrackingEnabled").jsonPrimitive.boolean)
        assertEquals(null, pauseDecision.getValue("calibrationLabel").jsonPrimitive.contentOrNull)
        assertEquals("DEVICE_CALIBRATION", decisions[1].jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals(
            DeviceCalibrationLabel.KNOWN_SAFE.name,
            decisions[1].jsonObject.getValue("calibrationLabel").jsonPrimitive.content,
        )
    }

    private fun watchlistDevice(
        fingerprint: String,
        lastSeenAt: Long,
    ): Device =
        Device(
            fingerprint = fingerprint,
            macAddress = "AA:BB:CC:11:22:44",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device $fingerprint",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Unknown Vendor",
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = true,
            isTrackingEnabled = true,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = lastSeenAt - 60_000L,
            lastSeenAt = lastSeenAt,
            rssi = -55,
            encounterCount = 1,
            calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
            evidence = emptyList(),
        )

    private fun watchlistReturnEvent(device: Device): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = SESSION_STARTED_AT + 20_000L,
            deviceFingerprint = device.fingerprint,
            observedMac = device.macAddress,
            eventType = AlertEvidenceEventType.WATCHLIST_RETURN,
            evidence =
                DetectionEvidence(
                    source = EvidenceSource.WATCHLIST,
                    confidence = DetectionConfidence.CRITICAL,
                    reasonText = "Watchlisted device returned after being offline.",
                    timestamp = SESSION_STARTED_AT + 20_000L,
                    rawValue = device.fingerprint,
                    parsedValue = "Watchlist return",
                    isPassive = true,
                    provenance = EvidenceProvenance.USER_ACTION,
                ),
        )

    private companion object {
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
        private const val EXPORT_DATE = SESSION_STARTED_AT + 120_000L
    }
}
