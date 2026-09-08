package io.blueeye.core.data.scanner

import io.blueeye.core.domain.scanner.ScannerIngestDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerRuntimeIngestDiagnosticsTest {
    @Test
    fun `ingest reducer keeps queue processing and persistence semantics separate`() {
        val reducer = ScannerIngestDiagnosticsReducer(windowMs = 60_000L)
        var diagnostics = ScannerIngestDiagnostics(windowStartedAt = 1_000L)
        val timestamp = 10_000L

        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.RawBleCallback("AA:BB:CC:11:22:33"), timestamp)
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.QueueAccepted(queueDepth = 3, queueHighWaterMark = 3),
            timestamp,
        )
        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.QueueRejected, timestamp)
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.QueueDropped(totalDropped = 1, queueDepth = 2),
            timestamp,
        )
        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.Coalesced, timestamp)
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.ProcessingStarted(queueDepth = 1, queueWaitMs = 17),
            timestamp,
        )
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.ProcessingCompleted(succeeded = true, durationMs = 23),
            timestamp,
        )
        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.ProvisionalDiscarded, timestamp)
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.DevicePersistence(
                deviceUpdated = true,
                deviceUpdateThrottled = true,
            ),
            timestamp,
        )
        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.SignalSample.WRITTEN, timestamp)
        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.SignalSample.THROTTLED, timestamp)
        diagnostics = reducer.reduce(diagnostics, ScannerIngestEvent.SignalSample.FAILED, timestamp)

        assertEquals(1L, diagnostics.rawBleCallbacksTotal)
        assertEquals(1L, diagnostics.enqueueAcceptedTotal)
        assertEquals(1L, diagnostics.enqueueRejectedTotal)
        assertEquals(1L, diagnostics.queueDroppedTotal)
        assertEquals(1L, diagnostics.coalescedTotal)
        assertEquals(1L, diagnostics.processingStartedTotal)
        assertEquals(1L, diagnostics.processingSucceededTotal)
        assertEquals(0L, diagnostics.processingFailedTotal)
        assertEquals(1L, diagnostics.provisionalDiscardedTotal)
        assertEquals(1L, diagnostics.persistedDeviceUpdatesTotal)
        assertEquals(1L, diagnostics.deviceUpdateThrottledTotal)
        assertEquals(1L, diagnostics.signalSamplesWrittenTotal)
        assertEquals(1L, diagnostics.signalSamplesThrottledTotal)
        assertEquals(1L, diagnostics.signalSampleWriteFailuresTotal)
        assertEquals(1, diagnostics.queueDepth)
        assertEquals(3, diagnostics.queueHighWaterMark)
        assertEquals(17L, diagnostics.lastQueueWaitMs)
        assertEquals(17L, diagnostics.totalQueueWaitMs)
        assertEquals(17L, diagnostics.maxQueueWaitMs)
        assertEquals(23L, diagnostics.lastProcessingDurationMs)
        assertEquals(23L, diagnostics.totalProcessingDurationMs)
        assertEquals(23L, diagnostics.maxProcessingDurationMs)
        assertEquals(1, diagnostics.rawBleCallbacksPerMinute)
        assertEquals(1, diagnostics.enqueueAcceptedPerMinute)
        assertEquals(1, diagnostics.coalescedPerMinute)
        assertEquals(1, diagnostics.processedPerMinute)
        assertEquals(1, diagnostics.persistedDeviceUpdatesPerMinute)
        assertEquals(1, diagnostics.signalSamplesWrittenPerMinute)
    }

    @Test
    fun `processing failure is not counted as processing success`() {
        val reducer = ScannerIngestDiagnosticsReducer()
        var diagnostics = ScannerIngestDiagnostics(windowStartedAt = 1_000L)

        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.ProcessingStarted(queueDepth = 0, queueWaitMs = 4),
            timestamp = 2_000L,
        )
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.ProcessingCompleted(succeeded = false, durationMs = 9),
            timestamp = 2_001L,
        )

        assertEquals(1L, diagnostics.processingStartedTotal)
        assertEquals(0L, diagnostics.processingSucceededTotal)
        assertEquals(1L, diagnostics.processingFailedTotal)
        assertEquals(9L, diagnostics.lastProcessingDurationMs)
        assertEquals(0, diagnostics.processedPerMinute)
    }

    @Test
    fun `rolling rates expire without resetting process lifetime totals`() {
        val reducer = ScannerIngestDiagnosticsReducer(windowMs = 1_000L)
        var diagnostics = ScannerIngestDiagnostics(windowStartedAt = 100L)

        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.RawBleCallback("AA:BB:CC:11:22:33"),
            timestamp = 1_000L,
        )
        diagnostics = reducer.reduce(
            diagnostics,
            ScannerIngestEvent.QueueAccepted(queueDepth = 1, queueHighWaterMark = 1),
            timestamp = 1_000L,
        )
        diagnostics = reducer.refresh(diagnostics, timestamp = 2_001L)

        assertEquals(1L, diagnostics.rawBleCallbacksTotal)
        assertEquals(1L, diagnostics.enqueueAcceptedTotal)
        assertEquals(0, diagnostics.rawBleCallbacksPerMinute)
        assertEquals(0, diagnostics.enqueueAcceptedPerMinute)
    }

    @Test
    fun `runtime store projects raw callback metadata and ingest totals together`() {
        val before = ScannerRuntimeDiagnosticsStore.diagnostics.value

        ScannerRuntimeDiagnosticsStore.recordIngest(
            ScannerIngestEvent.RawBleCallback("AA:BB:CC:11:22:33")
        )

        val after = ScannerRuntimeDiagnosticsStore.diagnostics.value
        assertEquals(before.ingest.rawBleCallbacksTotal + 1, after.ingest.rawBleCallbacksTotal)
        assertEquals("AA:BB:CC:11:22:33", after.lastBleMac)
        assertTrue(after.lastBleResultAt != null)
    }
}
