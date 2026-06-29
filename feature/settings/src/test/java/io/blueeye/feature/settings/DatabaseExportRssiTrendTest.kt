package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseExportRssiTrendTest {
    @Test
    fun `export includes per-device rssi trend for calibration`() {
        val device = device()
        val samples =
            listOf(
                sample(device.fingerprint, SESSION_STARTED_AT + 1_000L, rssi = -72),
                sample(device.fingerprint, SESSION_STARTED_AT + 2_000L, rssi = -68),
                sample(device.fingerprint, SESSION_STARTED_AT + 3_000L, rssi = -60),
                sample(device.fingerprint, SESSION_STARTED_AT + 4_000L, rssi = -56),
            )
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(device),
                    samples = samples,
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(device),
                            samples = samples,
                            label = DeviceCalibrationLabel.SUSPICIOUS,
                            startedAt = SESSION_STARTED_AT,
                            notes = "Moving session",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = SESSION_STARTED_AT + 60_000L,
                ),
            )
        val rssiTrend =
            export.getValue("session").jsonObject
                .getValue("deviceSummaries").jsonArray.single().jsonObject
                .getValue("sampleStats").jsonObject
                .getValue("rssiTrend").jsonObject

        assertEquals(RssiTrendDirection.STRENGTHENING.name, rssiTrend.getValue("direction").jsonPrimitive.content)
        assertEquals(-70.0, rssiTrend.getValue("firstWindowAverageRssi").jsonPrimitive.double, 0.001)
        assertEquals(-58.0, rssiTrend.getValue("lastWindowAverageRssi").jsonPrimitive.double, 0.001)
        assertEquals(12.0, rssiTrend.getValue("deltaRssi").jsonPrimitive.double, 0.001)
        assertEquals(2, rssiTrend.getValue("firstWindowSampleCount").jsonPrimitive.int)
        assertEquals(2, rssiTrend.getValue("lastWindowSampleCount").jsonPrimitive.int)
    }

    private fun device(): Device =
        Device(
            fingerprint = "moving-signal",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Moving signal",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Unknown Vendor",
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
            lastSeenAt = SESSION_STARTED_AT + 60_000L,
            rssi = -56,
            encounterCount = 4,
            calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
        )

    private fun sample(
        deviceFingerprint: String,
        timestamp: Long,
        rssi: Int,
    ): SignalSample =
        SignalSample(
            deviceFingerprint = deviceFingerprint,
            rssi = rssi,
            timestamp = timestamp,
        )

    private companion object {
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
    }
}
