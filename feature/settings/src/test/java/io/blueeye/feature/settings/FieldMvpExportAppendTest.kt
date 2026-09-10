package io.blueeye.feature.settings

import io.blueeye.core.domain.scanner.ScannerIngestDiagnostics
import io.blueeye.core.domain.scanner.ScannerRuntimeDiagnostics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class FieldMvpExportAppendTest {
    @Test
    fun `field export contains reconcilable ingest diagnostics`() {
        val ingest =
            ScannerIngestDiagnostics(
                rawBleCallbacksTotal = 10_000,
                rawBleCallbacksPerMinute = 600,
                enqueueAcceptedTotal = 100,
                enqueueAcceptedPerMinute = 6,
                enqueueRejectedTotal = 0,
                queueDroppedTotal = 0,
                coalescedTotal = 9_900,
                coalescedPerMinute = 594,
                processingStartedTotal = 100,
                processingSucceededTotal = 100,
                processedPerMinute = 6,
                persistedDeviceUpdatesTotal = 50,
                persistedDeviceUpdatesPerMinute = 3,
                signalSamplesWrittenTotal = 25,
                signalSamplesWrittenPerMinute = 2,
                queueDepth = 0,
                queueHighWaterMark = 100,
                lastQueueWaitMs = 12,
                maxQueueWaitMs = 44,
                lastProcessingDurationMs = 7,
                maxProcessingDurationMs = 31,
            )
        val uiState =
            SettingsUiState(
                scannerDiagnostics = ScannerRuntimeDiagnostics(ingest = ingest),
            )

        val root =
            Json.parseToJsonElement("{\"schemaVersion\":19}".withFieldMvpDiagnostics(uiState)).jsonObject
        val exportedIngest =
            root.getValue("fieldMvpDiagnostics").jsonObject
                .getValue("scanner").jsonObject
                .getValue("ingest").jsonObject

        assertEquals(10_000L, exportedIngest.getValue("rawBleCallbacksTotal").jsonPrimitive.long)
        assertEquals(100L, exportedIngest.getValue("enqueueAcceptedTotal").jsonPrimitive.long)
        assertEquals(9_900L, exportedIngest.getValue("coalescedTotal").jsonPrimitive.long)
        assertEquals(0L, exportedIngest.getValue("enqueueRejectedTotal").jsonPrimitive.long)
        assertEquals(0L, exportedIngest.getValue("queueDroppedTotal").jsonPrimitive.long)
        assertEquals(100L, exportedIngest.getValue("processingSucceededTotal").jsonPrimitive.long)
        assertEquals(50L, exportedIngest.getValue("persistedDeviceUpdatesTotal").jsonPrimitive.long)
        assertEquals(25L, exportedIngest.getValue("signalSamplesWrittenTotal").jsonPrimitive.long)
        assertEquals(100L, exportedIngest.getValue("queueHighWaterMark").jsonPrimitive.long)
        assertEquals(44L, exportedIngest.getValue("maxQueueWaitMs").jsonPrimitive.long)
        assertEquals(31L, exportedIngest.getValue("maxProcessingDurationMs").jsonPrimitive.long)
    }

    @Test
    fun `streamed diagnostics keep base export valid`() {
        val uiState = SettingsUiState(scannerDiagnostics = ScannerRuntimeDiagnostics())
        val base =
            """
            {
              "schemaVersion": 19,
              "sampleCount": 2,
              "signalSamples": [{"rssi":-60},{"rssi":-61}]
            }
            """.trimIndent()
        val writer = StringWriter()

        writer.writeWithFieldMvpDiagnostics(base, uiState)

        val root = Json.parseToJsonElement(writer.toString()).jsonObject
        assertEquals(19L, root.getValue("schemaVersion").jsonPrimitive.long)
        assertEquals(2L, root.getValue("sampleCount").jsonPrimitive.long)
        root.getValue("fieldMvpDiagnostics").jsonObject
    }
}
