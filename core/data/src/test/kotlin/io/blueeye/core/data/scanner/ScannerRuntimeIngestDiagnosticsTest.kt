package io.blueeye.core.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerRuntimeIngestDiagnosticsTest {
    @Test
    fun `ingest counters keep queue processing and persistence semantics separate`() {
        val before = ScannerRuntimeDiagnosticsStore.diagnostics.value.ingest
        val droppedTarget = before.queueDroppedTotal + 1

        ScannerRuntimeDiagnosticsStore.recordRawBleCallback("AA:BB:CC:11:22:33")
        ScannerRuntimeDiagnosticsStore.recordQueueAccepted(queueDepth = 3, queueHighWaterMark = 3)
        ScannerRuntimeDiagnosticsStore.recordQueueRejected()
        ScannerRuntimeDiagnosticsStore.recordDroppedQueueEvents(
            totalDropped = droppedTarget,
            queueDepth = 2,
        )
        ScannerRuntimeDiagnosticsStore.recordCoalescedEvent()
        ScannerRuntimeDiagnosticsStore.recordProcessingStarted(queueDepth = 1, queueWaitMs = 17)
        ScannerRuntimeDiagnosticsStore.recordProcessingSucceeded(processingDurationMs = 23)
        ScannerRuntimeDiagnosticsStore.recordProvisionalDiscarded()
        ScannerRuntimeDiagnosticsStore.recordDevicePersistenceOutcome(
            deviceUpdated = true,
            deviceUpdateThrottled = true,
        )
        ScannerRuntimeDiagnosticsStore.recordSignalSampleWritten()
        ScannerRuntimeDiagnosticsStore.recordSignalSampleThrottled()
        ScannerRuntimeDiagnosticsStore.recordSignalSampleWriteFailed()

        val after = ScannerRuntimeDiagnosticsStore.diagnostics.value.ingest
        assertEquals(before.rawBleCallbacksTotal + 1, after.rawBleCallbacksTotal)
        assertEquals(before.enqueueAcceptedTotal + 1, after.enqueueAcceptedTotal)
        assertEquals(before.enqueueRejectedTotal + 1, after.enqueueRejectedTotal)
        assertEquals(droppedTarget, after.queueDroppedTotal)
        assertEquals(before.coalescedTotal + 1, after.coalescedTotal)
        assertEquals(before.processingStartedTotal + 1, after.processingStartedTotal)
        assertEquals(before.processingSucceededTotal + 1, after.processingSucceededTotal)
        assertEquals(before.processingFailedTotal, after.processingFailedTotal)
        assertEquals(before.provisionalDiscardedTotal + 1, after.provisionalDiscardedTotal)
        assertEquals(before.persistedDeviceUpdatesTotal + 1, after.persistedDeviceUpdatesTotal)
        assertEquals(before.deviceUpdateThrottledTotal + 1, after.deviceUpdateThrottledTotal)
        assertEquals(before.signalSamplesWrittenTotal + 1, after.signalSamplesWrittenTotal)
        assertEquals(before.signalSamplesThrottledTotal + 1, after.signalSamplesThrottledTotal)
        assertEquals(before.signalSampleWriteFailuresTotal + 1, after.signalSampleWriteFailuresTotal)
        assertEquals(1, after.queueDepth)
        assertTrue(after.queueHighWaterMark >= 3)
        assertEquals(17L, after.lastQueueWaitMs)
        assertEquals(before.totalQueueWaitMs + 17L, after.totalQueueWaitMs)
        assertTrue(after.maxQueueWaitMs >= 17L)
        assertEquals(23L, after.lastProcessingDurationMs)
        assertEquals(before.totalProcessingDurationMs + 23L, after.totalProcessingDurationMs)
        assertTrue(after.maxProcessingDurationMs >= 23L)
        assertTrue(after.rawBleCallbacksPerMinute >= 1)
        assertTrue(after.enqueueAcceptedPerMinute >= 1)
        assertTrue(after.processedPerMinute >= 1)
        assertTrue(after.persistedDeviceUpdatesPerMinute >= 1)
        assertTrue(after.signalSamplesWrittenPerMinute >= 1)
    }

    @Test
    fun `processing failure is not counted as processing success`() {
        val before = ScannerRuntimeDiagnosticsStore.diagnostics.value.ingest

        ScannerRuntimeDiagnosticsStore.recordProcessingStarted(queueDepth = 0, queueWaitMs = 4)
        ScannerRuntimeDiagnosticsStore.recordProcessingFailed(processingDurationMs = 9)

        val after = ScannerRuntimeDiagnosticsStore.diagnostics.value.ingest
        assertEquals(before.processingStartedTotal + 1, after.processingStartedTotal)
        assertEquals(before.processingSucceededTotal, after.processingSucceededTotal)
        assertEquals(before.processingFailedTotal + 1, after.processingFailedTotal)
        assertEquals(9L, after.lastProcessingDurationMs)
    }
}
