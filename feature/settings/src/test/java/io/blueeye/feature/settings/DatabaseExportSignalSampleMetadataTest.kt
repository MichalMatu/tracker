package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseExportSignalSampleMetadataTest {
    @Test
    fun `export includes per-sample scan metadata`() {
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = emptyList(),
                    samples = listOf(richSample()),
                    session =
                        DatabaseExportSessionData(
                            devices = emptyList(),
                            samples = listOf(richSample()),
                            label = DeviceCalibrationLabel.UNKNOWN,
                            startedAt = EXPORT_DATE,
                            notes = "",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )

        val sample = export.getValue("signalSamples").jsonArray.single().jsonObject
        val session = export.getValue("session").jsonObject
        val privacy = export.getValue("privacyNotice").jsonObject

        assertEquals(1, export.getValue("samplesWithRawPayloads").jsonPrimitive.int)
        assertEquals(1, export.getValue("samplesWithScanMetadata").jsonPrimitive.int)
        assertEquals(1, session.getValue("samplesWithRawPayloads").jsonPrimitive.int)
        assertEquals(1, session.getValue("samplesWithScanMetadata").jsonPrimitive.int)
        assertEquals(true, privacy.getValue("containsRawPayloads").jsonPrimitive.boolean)
        assertEquals("AA:BB:CC:11:22:33", sample.getValue("observedMac").jsonPrimitive.content)
        assertEquals("BLE", sample.getValue("technology").jsonPrimitive.content)
        assertEquals("Tracker Tag", sample.getValue("deviceName").jsonPrimitive.content)
        assertEquals(DeviceType.TRACKER.name, sample.getValue("deviceType").jsonPrimitive.content)
        assertEquals("0201061AFF", sample.getValue("rawDataHex").jsonPrimitive.content)
        assertEquals("0x0133=010203", sample.getValue("manufacturerDataByIdHex").jsonPrimitive.content)
        assertEquals("0000feed-0000-1000-8000-00805f9b34fb", sample.getValue("serviceUuids").jsonPrimitive.content)
        assertEquals(
            "0000feed-0000-1000-8000-00805f9b34fb=AABB",
            sample.getValue("serviceDataByUuidHex").jsonPrimitive.content,
        )
        assertEquals(true, sample.getValue("isTactical").jsonPrimitive.boolean)
    }

    private fun richSample(): SignalSample =
        SignalSample(
            deviceFingerprint = "device-1",
            observedMac = "AA:BB:CC:11:22:33",
            technology = "BLE",
            deviceName = "Tracker Tag",
            deviceType = DeviceType.TRACKER.name,
            vendorName = "Axon",
            rssi = -57,
            timestamp = EXPORT_DATE,
            manufacturerId = 0x0133,
            manufacturerDataHex = "010203",
            manufacturerDataByIdHex = "0x0133=010203",
            serviceUuids = "0000feed-0000-1000-8000-00805f9b34fb",
            serviceDataByUuidHex = "0000feed-0000-1000-8000-00805f9b34fb=AABB",
            txPower = -8,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 2,
            advertisingIntervalMs = 1_000L,
            rawDataHex = "0201061AFF",
            trackingStatus = TrackingStatus.SAFE.name,
            followingScore = 0f,
            isTactical = true,
            tacticalCategory = "Public safety",
        )

    private companion object {
        private const val EXPORT_DATE = 1_789_000_120_000L
    }
}
