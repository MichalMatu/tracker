package io.blueeye.core.data.scanner

import io.blueeye.core.domain.scanner.ScannerIngestDiagnostics
import java.util.ArrayDeque

/**
 * Typed events for the scanner-ingest accounting pipeline.
 *
 * The event boundary keeps producers decoupled from counter implementation details and makes the
 * received -> queue -> processing -> persistence accounting semantics explicit in one place.
 */
internal sealed interface ScannerIngestEvent {
    data class RawBleCallback(val mac: String?) : ScannerIngestEvent

    data class QueueAccepted(
        val queueDepth: Int,
        val queueHighWaterMark: Int,
    ) : ScannerIngestEvent

    data object QueueRejected : ScannerIngestEvent

    data class QueueDropped(
        val totalDropped: Long,
        val queueDepth: Int? = null,
    ) : ScannerIngestEvent

    data object Coalesced : ScannerIngestEvent

    data class ProcessingStarted(
        val queueDepth: Int,
        val queueWaitMs: Long,
    ) : ScannerIngestEvent

    data class ProcessingCompleted(
        val succeeded: Boolean,
        val durationMs: Long,
    ) : ScannerIngestEvent

    data object ProvisionalDiscarded : ScannerIngestEvent

    data class DevicePersistence(
        val deviceUpdated: Boolean,
        val deviceUpdateThrottled: Boolean,
    ) : ScannerIngestEvent

    enum class SignalSample : ScannerIngestEvent {
        WRITTEN,
        THROTTLED,
        FAILED,
    }
}

/**
 * Deterministic process-local reducer for ingest diagnostics plus rolling one-minute rates.
 *
 * Synchronization and publication belong to [ScannerRuntimeDiagnosticsStore]. Keeping mutation
 * logic here makes the store a small runtime facade rather than a metrics God object.
 */
internal class ScannerIngestDiagnosticsReducer(
    private val windowMs: Long = WINDOW_MS,
) {
    private val rawBleEvents = ArrayDeque<Long>()
    private val enqueueAcceptedEvents = ArrayDeque<Long>()
    private val coalescedEvents = ArrayDeque<Long>()
    private val processedEvents = ArrayDeque<Long>()
    private val persistedDeviceEvents = ArrayDeque<Long>()
    private val signalSampleEvents = ArrayDeque<Long>()

    fun reduce(
        previous: ScannerIngestDiagnostics,
        event: ScannerIngestEvent,
        timestamp: Long,
    ): ScannerIngestDiagnostics {
        val updated =
            when (event) {
                is ScannerIngestEvent.RawBleCallback -> {
                    rawBleEvents.addLast(timestamp)
                    previous.copy(rawBleCallbacksTotal = previous.rawBleCallbacksTotal + 1)
                }
                is ScannerIngestEvent.QueueAccepted -> recordQueueAccepted(previous, event, timestamp)
                ScannerIngestEvent.QueueRejected ->
                    previous.copy(enqueueRejectedTotal = previous.enqueueRejectedTotal + 1)
                is ScannerIngestEvent.QueueDropped ->
                    previous.copy(
                        queueDroppedTotal = event.totalDropped,
                        queueDepth = event.queueDepth?.coerceAtLeast(0) ?: previous.queueDepth,
                    )
                ScannerIngestEvent.Coalesced -> {
                    coalescedEvents.addLast(timestamp)
                    previous.copy(coalescedTotal = previous.coalescedTotal + 1)
                }
                is ScannerIngestEvent.ProcessingStarted -> recordProcessingStarted(previous, event)
                is ScannerIngestEvent.ProcessingCompleted ->
                    recordProcessingCompleted(previous, event, timestamp)
                ScannerIngestEvent.ProvisionalDiscarded ->
                    previous.copy(
                        provisionalDiscardedTotal = previous.provisionalDiscardedTotal + 1,
                    )
                is ScannerIngestEvent.DevicePersistence ->
                    recordDevicePersistence(previous, event, timestamp)
                is ScannerIngestEvent.SignalSample -> recordSignalSample(previous, event, timestamp)
            }

        return refresh(updated, timestamp)
    }

    fun refresh(
        previous: ScannerIngestDiagnostics,
        timestamp: Long,
    ): ScannerIngestDiagnostics {
        prune(rawBleEvents, timestamp)
        prune(enqueueAcceptedEvents, timestamp)
        prune(coalescedEvents, timestamp)
        prune(processedEvents, timestamp)
        prune(persistedDeviceEvents, timestamp)
        prune(signalSampleEvents, timestamp)
        return previous.copy(
            rawBleCallbacksPerMinute = rawBleEvents.size,
            enqueueAcceptedPerMinute = enqueueAcceptedEvents.size,
            coalescedPerMinute = coalescedEvents.size,
            processedPerMinute = processedEvents.size,
            persistedDeviceUpdatesPerMinute = persistedDeviceEvents.size,
            signalSamplesWrittenPerMinute = signalSampleEvents.size,
        )
    }

    private fun recordQueueAccepted(
        previous: ScannerIngestDiagnostics,
        event: ScannerIngestEvent.QueueAccepted,
        timestamp: Long,
    ): ScannerIngestDiagnostics {
        enqueueAcceptedEvents.addLast(timestamp)
        return previous.copy(
            enqueueAcceptedTotal = previous.enqueueAcceptedTotal + 1,
            queueDepth = event.queueDepth.coerceAtLeast(0),
            queueHighWaterMark = maxOf(previous.queueHighWaterMark, event.queueHighWaterMark),
        )
    }

    private fun recordProcessingStarted(
        previous: ScannerIngestDiagnostics,
        event: ScannerIngestEvent.ProcessingStarted,
    ): ScannerIngestDiagnostics {
        val safeQueueWaitMs = event.queueWaitMs.coerceAtLeast(0L)
        return previous.copy(
            processingStartedTotal = previous.processingStartedTotal + 1,
            queueDepth = event.queueDepth.coerceAtLeast(0),
            lastQueueWaitMs = safeQueueWaitMs,
            totalQueueWaitMs = previous.totalQueueWaitMs + safeQueueWaitMs,
            maxQueueWaitMs = maxOf(previous.maxQueueWaitMs, safeQueueWaitMs),
        )
    }

    private fun recordProcessingCompleted(
        previous: ScannerIngestDiagnostics,
        event: ScannerIngestEvent.ProcessingCompleted,
        timestamp: Long,
    ): ScannerIngestDiagnostics {
        val safeDurationMs = event.durationMs.coerceAtLeast(0L)
        if (event.succeeded) processedEvents.addLast(timestamp)
        return previous.copy(
            processingSucceededTotal =
                previous.processingSucceededTotal + if (event.succeeded) 1 else 0,
            processingFailedTotal =
                previous.processingFailedTotal + if (event.succeeded) 0 else 1,
            lastProcessingDurationMs = safeDurationMs,
            totalProcessingDurationMs = previous.totalProcessingDurationMs + safeDurationMs,
            maxProcessingDurationMs = maxOf(previous.maxProcessingDurationMs, safeDurationMs),
        )
    }

    private fun recordDevicePersistence(
        previous: ScannerIngestDiagnostics,
        event: ScannerIngestEvent.DevicePersistence,
        timestamp: Long,
    ): ScannerIngestDiagnostics {
        if (event.deviceUpdated) persistedDeviceEvents.addLast(timestamp)
        return previous.copy(
            persistedDeviceUpdatesTotal =
                previous.persistedDeviceUpdatesTotal + if (event.deviceUpdated) 1 else 0,
            deviceUpdateThrottledTotal =
                previous.deviceUpdateThrottledTotal + if (event.deviceUpdateThrottled) 1 else 0,
        )
    }

    private fun recordSignalSample(
        previous: ScannerIngestDiagnostics,
        event: ScannerIngestEvent.SignalSample,
        timestamp: Long,
    ): ScannerIngestDiagnostics =
        when (event) {
            ScannerIngestEvent.SignalSample.WRITTEN -> {
                signalSampleEvents.addLast(timestamp)
                previous.copy(signalSamplesWrittenTotal = previous.signalSamplesWrittenTotal + 1)
            }
            ScannerIngestEvent.SignalSample.THROTTLED ->
                previous.copy(
                    signalSamplesThrottledTotal = previous.signalSamplesThrottledTotal + 1,
                )
            ScannerIngestEvent.SignalSample.FAILED ->
                previous.copy(
                    signalSampleWriteFailuresTotal = previous.signalSampleWriteFailuresTotal + 1,
                )
        }

    private fun prune(
        events: ArrayDeque<Long>,
        timestamp: Long,
    ) {
        while (events.peekFirst()?.let { firstEventAt -> timestamp - firstEventAt > windowMs } == true) {
            events.removeFirst()
        }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
    }
}
