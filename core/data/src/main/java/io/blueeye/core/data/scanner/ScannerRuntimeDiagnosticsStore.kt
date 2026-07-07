package io.blueeye.core.data.scanner

import io.blueeye.core.domain.scanner.ScannerRuntimeDiagnostics
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

object ScannerRuntimeDiagnosticsStore {
    private const val WINDOW_MS = 60_000L

    private val lock = Any()
    private val bleEvents = ArrayDeque<Long>()
    private val classicEvents = ArrayDeque<Long>()
    private val _diagnostics = MutableStateFlow(ScannerRuntimeDiagnostics(updatedAt = now()))

    val diagnostics: StateFlow<ScannerRuntimeDiagnostics> = _diagnostics.asStateFlow()

    fun recordState(state: ScannerRuntimeState) {
        synchronized(lock) {
            val timestamp = now()
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    state = state,
                    startedAt = state.startedAt(previous.startedAt, timestamp),
                    lastScanError = if (state is ScannerRuntimeState.Error) state.message else previous.lastScanError,
                    updatedAt = timestamp,
                )
        }
    }

    fun recordScanError(message: String) {
        synchronized(lock) {
            val timestamp = now()
            _diagnostics.value =
                _diagnostics.value.copy(
                    state = ScannerRuntimeState.Error(message),
                    lastScanError = message,
                    updatedAt = timestamp,
                )
        }
    }

    fun recordBleResult(mac: String?) {
        synchronized(lock) {
            val timestamp = now()
            bleEvents.addLast(timestamp)
            prune(bleEvents, timestamp)
            prune(classicEvents, timestamp)
            val previous = _diagnostics.value
            _diagnostics.value =
                previous.copy(
                    state = ScannerRuntimeState.Running,
                    startedAt = previous.startedAt ?: timestamp,
                    lastBleResultAt = timestamp,
                    lastBleMac = mac,
                    bleResultsPerMinute = bleEvents.size,
                    classicResultsPerMinute = classicEvents.size,
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
                    updatedAt = timestamp,
                )
        }
    }

    fun recordDroppedQueueEvents(totalDropped: Long) {
        synchronized(lock) {
            val timestamp = now()
            _diagnostics.value =
                _diagnostics.value.copy(
                    droppedQueueEvents = totalDropped,
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
