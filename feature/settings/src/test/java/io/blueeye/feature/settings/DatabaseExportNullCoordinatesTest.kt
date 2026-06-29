package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.SignalSample
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DatabaseExportNullCoordinatesTest {
    @Test
    fun `export keeps null sample coordinates structured`() {
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = emptyList(),
                    samples = listOf(SignalSample(deviceFingerprint = "unknown", rssi = -57, timestamp = EXPORT_DATE)),
                    session =
                        DatabaseExportSessionData(
                            devices = emptyList(),
                            samples = emptyList(),
                            label = DeviceCalibrationLabel.UNKNOWN,
                            startedAt = 0L,
                            notes = "",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )

        val exportedSample = export.getValue("signalSamples").jsonArray.single().jsonObject
        val reviewReadiness =
            export.getValue("session").jsonObject
                .getValue("reviewReadiness")
                .jsonObject

        assertEquals(null, exportedSample.getValue("latitude").jsonPrimitive.contentOrNull)
        assertEquals(null, exportedSample.getValue("longitude").jsonPrimitive.contentOrNull)
        assertEquals(null, exportedSample.getValue("locationAccuracy").jsonPrimitive.contentOrNull)
        assertFalse(reviewReadiness.getValue("readyForHeuristicReview").jsonPrimitive.boolean)
        assertEquals(
            listOf("SESSION_LABEL", "SESSION_DEVICES", "RSSI_SAMPLES"),
            reviewReadiness.stringArray("blockers"),
        )
        assertEquals(
            listOf("SESSION_NOTES", "ATTENTION_EVIDENCE"),
            reviewReadiness.stringArray("warnings"),
        )
    }

    private fun JsonObject.stringArray(key: String): List<String> =
        getValue(key).jsonArray.map { item ->
            item.jsonPrimitive.content
        }

    private companion object {
        private const val EXPORT_DATE = 1_789_000_120_000L
    }
}
