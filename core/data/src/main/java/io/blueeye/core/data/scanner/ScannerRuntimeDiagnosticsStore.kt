package io.blueeye.core.data.scanner

import io.blueeye.core.domain.scanner.ScannerIngestDiagnostics
import io.blueeye.core.domain.scanner.ScannerLifecycleTransition
import io.blueeye.core.domain.scanner.ScannerRuntimeDiagnostics
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

@Suppress("TooManyFunctions")
object ScannerRuntimeDiagnosticsStore {
    private const val WINDOW_MS = 60_000L

    private val lock = Any()
    private val bleEvents = ArrayDeque<Long>()
    private val classicEvents = ArrayDeque<Long>()
    private val rawBleEvents = ArrayDeque<Long>()
    private val enqueueAcceptedEvents = ArrayDeque<Long>()
    private val coalescedEvents = ArrayDeque<Long>()
    private val processedEvents = ArrayDeque<Long>()
    private val persistedDeviceEvents = ArrayDeque<Long>()
    private val signalSampleEvents = ArrayDeque<Long>()
    private val initializedAt = now()
    private val _diagnostics =
        MutableStateFlow(
            ScannerRuntimeDiagnostics(
                ingest = ScannerIngestDiagnostics(windowStartedAt = initializedAt),
                updatedAt = initializedAt,
            )
        )

    val diagnostics: StateFlow<ScannerRuntimeDiagnostics> = _diagnostics.asStateFlow()

    fun recordState(state: ScannerRuntimeState) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    state = state,
                    startedAt = state.startedAt(previous.startedAt, timestamp),
                    ingest = previous.ingest.withRollingRates(timestamp),
                    lastScanError = if (state is ScannerRuntimeState.Error) state.message else previous.lastScanError,
                    updatedAt = timestamp,
                )
        }
    }

    fun recordLifecycleTransition(transition: ScannerLifecycleTransition) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest = previous.ingest.withRollingRates(timestamp),
                    lastLifecycleTransition = transition,
                    lastLifecycleTransitionAt = timestamp,
                    lifecycleTransitionCount = previous.lifecycleTransitionCount + 1,
                    updatedAt = timestamp,
                )
        }
    }

    fun recordScanError(message: String) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    state = ScannerRuntimeState.Error(message),
                    ingest = previous.ingest.withRollingRates(timestamp),
                    lastScanError = message,
                    updatedAt = timestamp,
                )
        }
    }

    fun recordRawBleCallback(mac: String?) {
        synchronized(lock) {
            val timestamp = now()
            rawBleEvents.addLast(timestamp)
            val previous = _diagnostics.value
            val ingest =
                previous.ingest
                    .copy(rawBleCallbacksTotal = previous.ingest.rawBleCallbacksTotal + 1)
                    .withRollingRates(timestamp)
            _diagnostics.value =
                previous.copy(
                    state = ScannerRuntimeState.Running,
                    startedAt = previous.startedAt ?: timestamp,
                    lastBleResultAt = timestamp,
                    lastBleMac = mac,
                    ingest = ingest,
                    updatedAt = timestamp,
                )
        }
    }

    /** Legacy post-queue BLE rate retained for compatibility while ingest metrics are canonical. */
    fun recordBleResult() {
        synchronized(lock) {
            val timestamp = now()
            bleEvents.addLast(timestamp)
            prune(bleEvents, timestamp)
            prune(classicEvents, timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    bleResultsPerMinute = bleEvents.size,
                    classicResultsPerMinute = classicEvents.size,
                    ingest = previous.ingest.withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordClassicResult(mac: String?) {
        synchronized(lock) {
            val timestamp = now()
            classicEvents.addLast(timestamp)
            prune(bleEvents, timestamp)
            prune(classicEvents, timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    state = ScannerRuntimeState.Running,
                    startedAt = previous.startedAt ?: timestamp,
                    lastClassicResultAt = timestamp,
                    lastClassicMac = mac,
                    bleResultsPerMinute = bleEvents.size,
                    classicResultsPerMinute = classicEvents.size,
                    ingest = previous.ingest.withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordQueueAccepted(
        queueDepth: Int,
        queueHighWaterMark: Int,
    ) {
        synchronized(lock) {
            val timestamp = now()
            enqueueAcceptedEvents.addLast(timestamp)
            val previous = _diagnostics.value
            val ingest = previous.ingest
            _diagnostics.value =
                previous.copy(
                    ingest =
                        ingest.copy(
                            enqueueAcceptedTotal = ingest.enqueueAcceptedTotal + 1,
                            queueDepth = queueDepth.coerceAtLeast(0),
                            queueHighWaterMark = maxOf(ingest.queueHighWaterMark, queueHighWaterMark),
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordQueueRejected() {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest =
                        previous.ingest.copy(
                            enqueueRejectedTotal = previous.ingest.enqueueRejectedTotal + 1,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordDroppedQueueEvents(
        totalDropped: Long,
        queueDepth: Int? = null,
    ) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    droppedQueueEvents = totalDropped,
                    ingest =
                        previous.ingest.copy(
                            queueDroppedTotal = totalDropped,
                            queueDepth = queueDepth?.coerceAtLeast(0) ?: previous.ingest.queueDepth,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordCoalescedEvent() {
        synchronized(lock) {
            val timestamp = now()
            coalescedEvents.addLast(timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest =
                        previous.ingest.copy(
                            coalescedTotal = previous.ingest.coalescedTotal + 1,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordProcessingStarted(
        queueDepth: Int,
        queueWaitMs: Long,
    ) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            val ingest = previous.ingest
            val safeQueueWaitMs = queueWaitMs.coerceAtLeast(0L)
            _diagnostics.value =
                previous.copy(
                    ingest =
                        ingest.copy(
                            processingStartedTotal = ingest.processingStartedTotal + 1,
                            queueDepth = queueDepth.coerceAtLeast(0),
                            lastQueueWaitMs = safeQueueWaitMs,
                            totalQueueWaitMs = ingest.totalQueueWaitMs + safeQueueWaitMs,
                            maxQueueWaitMs = maxOf(ingest.maxQueueWaitMs, safeQueueWaitMs),
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordProcessingSucceeded(processingDurationMs: Long) {
        synchronized(lock) {
            val timestamp = now()
            processedEvents.addLast(timestamp)
            val previous = _diagnostics.value
            val ingest = previous.ingest
            val safeDurationMs = processingDurationMs.coerceAtLeast(0L)
            _diagnostics.value =
                previous.copy(
                    ingest =
                        ingest.copy(
                            processingSucceededTotal = ingest.processingSucceededTotal + 1,
                            lastProcessingDurationMs = safeDurationMs,
                            totalProcessingDurationMs = ingest.totalProcessingDurationMs + safeDurationMs,
                            maxProcessingDurationMs = maxOf(ingest.maxProcessingDurationMs, safeDurationMs),
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordProcessingFailed(processingDurationMs: Long) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            val ingest = previous.ingest
            val safeDurationMs = processingDurationMs.coerceAtLeast(0L)
            _diagnostics.value =
                previous.copy(
                    ingest =
                        ingest.copy(
                            processingFailedTotal = ingest.processingFailedTotal + 1,
                            lastProcessingDurationMs = safeDurationMs,
                            totalProcessingDurationMs = ingest.totalProcessingDurationMs + safeDurationMs,
                            maxProcessingDurationMs = maxOf(ingest.maxProcessingDurationMs, safeDurationMs),
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordProvisionalDiscarded() {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest =
                        previous.ingest.copy(
                            provisionalDiscardedTotal = previous.ingest.provisionalDiscardedTotal + 1,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordDevicePersistenceOutcome(
        deviceUpdated: Boolean,
        deviceUpdateThrottled: Boolean,
    ) {
        synchronized(lock) {
            val timestamp = now()
            if (deviceUpdated) persistedDeviceEvents.addLast(timestamp)
            val previous = _diagnostics.value
            val ingest = previous.ingest
            _diagnostics.value =
                previous.copy(
                    ingest =
                        ingest.copy(
                            persistedDeviceUpdatesTotal =
                                ingest.persistedDeviceUpdatesTotal + if (deviceUpdated) 1 else 0,
                            deviceUpdateThrottledTotal =
                                ingest.deviceUpdateThrottledTotal + if (deviceUpdateThrottled) 1 else 0,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordSignalSampleWritten() {
        synchronized(lock) {
            val timestamp = now()
            signalSampleEvents.addLast(timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest =
                        previous.ingest.copy(
                            signalSamplesWrittenTotal = previous.ingest.signalSamplesWrittenTotal + 1,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordSignalSampleThrottled() {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest =
                        previous.ingest.copy(
                            signalSamplesThrottledTotal = previous.ingest.signalSamplesThrottledTotal + 1,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordSignalSampleWriteFailed() {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    ingest =
                        previous.ingest.copy(
                            signalSampleWriteFailuresTotal = previous.ingest.signalSampleWriteFailuresTotal + 1,
                        ).withRollingRates(timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    private fun ScannerRuntimeState.startedAt(
        previousStartedAt: Long?,
        timestamp: Long,
    ): Long? =
        when (this) {
            ScannerRuntimeState.Idle -> null
            ScannerRuntimeState.Starting,
            ScannerRuntimeState.Running,
            is ScannerRuntimeState.Error,
            -> previousStartedAt ?: timestamp
        }

    private fun ScannerIngestDiagnostics.withRollingRates(timestamp: Long): ScannerIngestDiagnostics {
        prune(rawBleEvents, timestamp)
        prune(enqueueAcceptedEvents, timestamp)
        prune(coalescedEvents, timestamp)
        prune(processedEvents, timestamp)
        prune(persistedDeviceEvents, timestamp)
        prune(signalSampleEvents, timestamp)
        return copy(
            rawBleCallbacksPerMinute = rawBleEvents.size,
            enqueueAcceptedPerMinute = enqueueAcceptedEvents.size,
            coalescedPerMinute = coalescedEvents.size,
            processedPerMinute = processedEvents.size,
            persistedDeviceUpdatesPerMinute = persistedDeviceEvents.size,
            signalSamplesWrittenPerMinute = signalSampleEvents.size,
        )
    }

    private fun prune(
        events: ArrayDeque<Long>,
        timestamp: Long,
    ) {
        while (events.peekFirst()?.let { firstEventAt -> timestamp - firstEventAt > WINDOW_MS } == true) {
            events.removeFirst()
        }
    }

    private fun now(): Long = System.currentTimeMillis()
}
