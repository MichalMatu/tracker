package io.blueeye.core.data.scanner

import io.blueeye.core.domain.scanner.ScannerIngestDiagnostics
import io.blueeye.core.domain.scanner.ScannerLifecycleTransition
import io.blueeye.core.domain.scanner.ScannerRuntimeDiagnostics
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Process-lifetime scanner diagnostics facade.
 *
 * Runtime/lifecycle state is owned here. Detailed ingest accounting is reduced by
 * [ScannerIngestDiagnosticsReducer], which keeps queue/processing/persistence semantics separate
 * from lifecycle publication and avoids one oversized metrics object.
 */
object ScannerRuntimeDiagnosticsStore {
    private const val WINDOW_MS = 60_000L

    private val lock = Any()
    private val bleEvents = ArrayDeque<Long>()
    private val classicEvents = ArrayDeque<Long>()
    private val ingestReducer = ScannerIngestDiagnosticsReducer()
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
                    ingest = ingestReducer.refresh(previous.ingest, timestamp),
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
                    ingest = ingestReducer.refresh(previous.ingest, timestamp),
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
                    ingest = ingestReducer.refresh(previous.ingest, timestamp),
                    lastScanError = message,
                    updatedAt = timestamp,
                )
        }
    }

    /** Legacy post-queue BLE rate retained for compatibility while ingest metrics are canonical. */
    fun recordBleResult() {
        synchronized(lock) {
            val timestamp = now()
            bleEvents.addLast(timestamp)
            refreshLegacyRates(timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    bleResultsPerMinute = bleEvents.size,
                    classicResultsPerMinute = classicEvents.size,
                    ingest = ingestReducer.refresh(previous.ingest, timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    fun recordClassicResult(mac: String?) {
        synchronized(lock) {
            val timestamp = now()
            classicEvents.addLast(timestamp)
            refreshLegacyRates(timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    state = ScannerRuntimeState.Running,
                    startedAt = previous.startedAt ?: timestamp,
                    lastClassicResultAt = timestamp,
                    lastClassicMac = mac,
                    bleResultsPerMinute = bleEvents.size,
                    classicResultsPerMinute = classicEvents.size,
                    ingest = ingestReducer.refresh(previous.ingest, timestamp),
                    updatedAt = timestamp,
                )
        }
    }

    internal fun recordIngest(event: ScannerIngestEvent) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            val rawBle = event as? ScannerIngestEvent.RawBleCallback
            val dropped = event as? ScannerIngestEvent.QueueDropped
            _diagnostics.value =
                previous.copy(
                    state = if (rawBle != null) ScannerRuntimeState.Running else previous.state,
                    startedAt = if (rawBle != null) previous.startedAt ?: timestamp else previous.startedAt,
                    lastBleResultAt = if (rawBle != null) timestamp else previous.lastBleResultAt,
                    lastBleMac = if (rawBle != null) rawBle.mac else previous.lastBleMac,
                    droppedQueueEvents = dropped?.totalDropped ?: previous.droppedQueueEvents,
                    ingest = ingestReducer.reduce(previous.ingest, event, timestamp),
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

    private fun refreshLegacyRates(timestamp: Long) {
        prune(bleEvents, timestamp)
        prune(classicEvents, timestamp)
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
