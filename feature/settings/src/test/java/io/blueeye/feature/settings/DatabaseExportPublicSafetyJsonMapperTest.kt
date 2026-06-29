package io.blueeye.feature.settings

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseExportPublicSafetyJsonMapperTest {
    @Test
    fun `export includes public safety calibration review decisions`() {
        val publicSafetyDevice = publicSafetyDevice()
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(publicSafetyDevice),
                    samples = emptyList(),
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(publicSafetyDevice),
                            samples = emptyList(),
                            label = DeviceCalibrationLabel.UNKNOWN,
                            startedAt = SESSION_STARTED_AT,
                            notes = "",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )

        val reviewDeviceQueue =
            export.getValue("session").jsonObject
                .getValue("reviewDeviceQueue").jsonArray.single().jsonObject
        val decisions = reviewDeviceQueue.getValue("decisions").jsonArray

        assertEquals("Public-safety-like signal", reviewDeviceQueue.getValue("reasonText").jsonPrimitive.content)
        assertEquals("DEVICE_CALIBRATION", decisions[0].jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals(
            DeviceCalibrationLabel.KNOWN_SAFE.name,
            decisions[0].jsonObject.getValue("calibrationLabel").jsonPrimitive.content,
        )
        assertEquals("False positive", decisions[1].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(
            DeviceCalibrationLabel.FALSE_POSITIVE.name,
            decisions[1].jsonObject.getValue("calibrationLabel").jsonPrimitive.content,
        )
    }

    private fun publicSafetyDevice(): Device =
        Device(
            fingerprint = "axon-like",
            macAddress = "AA:BB:CC:11:22:46",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Axon-like signal",
            deviceType = DeviceType.BODY_CAMERA,
            vendorName = null,
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = SESSION_STARTED_AT,
            lastSeenAt = SESSION_STARTED_AT + 15_000L,
            rssi = -55,
            encounterCount = 1,
            evidence = listOf(publicSafetyEvidence()),
        )

    private fun publicSafetyEvidence(): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.SERVICE_UUID,
            confidence = DetectionConfidence.HIGH,
            reasonText = "Service UUID is consistent with Axon Body Camera.",
            timestamp = SESSION_STARTED_AT + 15_000L,
            rawValue = "0000fd8e-0000-1000-8000-00805f9b34fb",
            parsedValue = DeviceType.BODY_CAMERA.name,
            isPassive = true,
        )

    private companion object {
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
        private const val EXPORT_DATE = SESSION_STARTED_AT + 120_000L
    }
}
