package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseExportDecodedSignalJsonMapperTest {
    @Test
    fun `export includes decoded scanner signal session summary`() {
        val decodedDevice =
            device().copy(
                sensorData = "Bat: 91%",
                beaconType = "iBeacon",
                lastRawData = "0201061AFF",
            )

        val export = buildExport(listOf(decodedDevice))
        val session = export.getValue("session").jsonObject
        val kindCounts = session.getValue("decodedSignalKindCounts").jsonObject
        val decodedSignal = session.getValue("decodedSignals").jsonArray.single().jsonObject
        val deviceSummaryDecodedSignal =
            session.getValue("deviceSummaries").jsonArray.single().jsonObject
                .getValue("decodedSignal")
                .jsonObject

        assertEquals(1, session.getValue("decodedSignalDeviceCount").jsonPrimitive.int)
        assertEquals(1, kindCounts.getValue("BEACON_TYPE").jsonPrimitive.int)
        assertEquals(1, kindCounts.getValue("SENSOR_DATA").jsonPrimitive.int)
        assertEquals(1, kindCounts.getValue("RAW_PAYLOAD").jsonPrimitive.int)
        assertDecodedSignal(decodedSignal)
        assertDecodedSignal(deviceSummaryDecodedSignal)
    }

    @Test
    fun `export uses null decoded signal summary when device has no decoded data`() {
        val export = buildExport(listOf(device()))
        val session = export.getValue("session").jsonObject
        val kindCounts = session.getValue("decodedSignalKindCounts").jsonObject
        val deviceSummaryDecodedSignal =
            session.getValue("deviceSummaries").jsonArray.single().jsonObject
                .getValue("decodedSignal")

        assertEquals(0, session.getValue("decodedSignalDeviceCount").jsonPrimitive.int)
        assertEquals(0, kindCounts.getValue("BEACON_TYPE").jsonPrimitive.int)
        assertEquals(0, kindCounts.getValue("SENSOR_DATA").jsonPrimitive.int)
        assertEquals(0, kindCounts.getValue("RAW_PAYLOAD").jsonPrimitive.int)
        assertTrue(session.getValue("decodedSignals").jsonArray.isEmpty())
        assertEquals(JsonNull, deviceSummaryDecodedSignal)
    }

    private fun assertDecodedSignal(signal: JsonObject) {
        assertEquals(DEVICE_FINGERPRINT, signal.getValue("fingerprint").jsonPrimitive.content)
        assertEquals("Device decoded-device", signal.getValue("displayName").jsonPrimitive.content)
        assertEquals("iBeacon", signal.getValue("beaconType").jsonPrimitive.content)
        assertEquals("Bat: 91%", signal.getValue("sensorData").jsonPrimitive.content)
        assertTrue(signal.getValue("hasRawPayload").jsonPrimitive.boolean)
        assertEquals(5, signal.getValue("rawPayloadByteLength").jsonPrimitive.int)
        assertEquals(
            listOf("BEACON_TYPE", "SENSOR_DATA", "RAW_PAYLOAD"),
            signal.getValue("kinds").jsonArray.map { kind -> kind.jsonPrimitive.content },
        )
    }

    private fun buildExport(devices: List<Device>) =
        DatabaseExportJsonMapper.buildExport(
            DatabaseExportData(
                devices = devices,
                samples = emptyList(),
                session =
                    DatabaseExportSessionData(
                        devices = devices,
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

    private fun device(): Device =
        Device(
            fingerprint = DEVICE_FINGERPRINT,
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device decoded-device",
            deviceType = DeviceType.UNKNOWN,
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
            lastSeenAt = SESSION_STARTED_AT + 10_000L,
            rssi = -55,
            encounterCount = 1,
        )

    private companion object {
        private const val DEVICE_FINGERPRINT = "decoded-device"
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
        private const val EXPORT_DATE = SESSION_STARTED_AT + 120_000L
    }
}
