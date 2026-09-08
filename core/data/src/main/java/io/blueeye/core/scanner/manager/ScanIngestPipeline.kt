package io.blueeye.core.scanner.manager

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.util.Log
import io.blueeye.core.data.scanner.ScannerIngestEvent
import io.blueeye.core.data.scanner.ScannerRuntimeDiagnosticsStore
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.scanner.extractor.ScanResultExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private sealed interface ScanEvent {
    val queueKey: String
    val receivedAtMonotonicMs: Long

    data class BleResult(
        val result: ScanResult,
        val mac: String,
        val observedAtEpochMs: Long,
        override val receivedAtMonotonicMs: Long,
    ) : ScanEvent {
        override val queueKey: String = "ble:$mac"
    }

    data class ClassicResult(
        val mac: String,
        val name: String?,
        val rssi: Int,
        val classOfDevice: Int?,
        val serviceUuids: List<String>,
        override val receivedAtMonotonicMs: Long,
    ) : ScanEvent {
        override val queueKey: String = "classic:$mac"
    }
}

/**
 * Process-lifetime bounded ingest pipeline for BLE/Classic scan observations.
 *
 * Hardware scan ownership stays in [BleScanner]. This class owns only callback accounting,
 * latest-per-device queueing and sequential repository processing. It intentionally survives
 * scanner Start/Stop transitions for the lifetime of the scanner scope, matching the accepted
 * Phase 2 lifecycle contract while isolating Phase 3 backpressure concerns.
 */
internal class ScanIngestPipeline(
    scope: CoroutineScope,
    private val repository: DeviceRepository,
    private val scanResultExtractor: ScanResultExtractor,
    private val onProcessingError: (String) -> Unit,
) {
    private val rejectedScanEvents = AtomicLong(0L)
    private val scanEventBuffer =
        LatestPerKeyScanBuffer<String, ScanEvent>(capacity = SCAN_EVENT_BUFFER_CAPACITY)

    init {
        scope.launch { processEvents() }
    }

    @SuppressLint("MissingPermission")
    fun onBleResult(result: ScanResult) {
        val mac = result.device.address
        val receivedAtMonotonicMs = monotonicNowMs()
        val observedAtEpochMs = System.currentTimeMillis()
        ScannerRuntimeDiagnosticsStore.recordIngest(ScannerIngestEvent.RawBleCallback(mac))
        enqueue(
            ScanEvent.BleResult(
                result = result,
                mac = mac,
                observedAtEpochMs = observedAtEpochMs,
                receivedAtMonotonicMs = receivedAtMonotonicMs,
            )
        )
    }

    fun onClassicResult(
        mac: String,
        name: String?,
        rssi: Int,
        classOfDevice: Int?,
        serviceUuids: List<String>,
    ) {
        enqueue(
            ScanEvent.ClassicResult(
                mac = mac,
                name = name,
                rssi = rssi,
                classOfDevice = classOfDevice,
                serviceUuids = serviceUuids,
                receivedAtMonotonicMs = monotonicNowMs(),
            )
        )
    }

    private suspend fun processEvents() {
        while (true) {
            val bufferedEvent = scanEventBuffer.receive()
            val event = bufferedEvent.value
            val processingStartedAt = monotonicNowMs()
            ScannerRuntimeDiagnosticsStore.recordIngest(
                ScannerIngestEvent.ProcessingStarted(
                    queueDepth = bufferedEvent.queueDepthAfterDequeue,
                    queueWaitMs = processingStartedAt - event.receivedAtMonotonicMs,
                )
            )

            val succeeded =
                try {
                    when (event) {
                        is ScanEvent.BleResult -> handleBleResult(event)
                        is ScanEvent.ClassicResult -> handleClassicResult(event)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    Log.e(TAG, "Error processing scan event", error)
                    false
                }

            ScannerRuntimeDiagnosticsStore.recordIngest(
                ScannerIngestEvent.ProcessingCompleted(
                    succeeded = succeeded,
                    durationMs = monotonicNowMs() - processingStartedAt,
                )
            )
        }
    }

    private fun enqueue(event: ScanEvent) {
        when (
            val offerResult = scanEventBuffer.offer(
                key = event.queueKey,
                value = event,
            )
        ) {
            is ScanBufferOfferResult.Enqueued ->
                ScannerRuntimeDiagnosticsStore.recordIngest(
                    ScannerIngestEvent.QueueAccepted(
                        queueDepth = offerResult.queueDepth,
                        queueHighWaterMark = offerResult.queueHighWaterMark,
                    )
                )
            is ScanBufferOfferResult.Coalesced ->
                ScannerRuntimeDiagnosticsStore.recordIngest(ScannerIngestEvent.Coalesced)
            is ScanBufferOfferResult.Rejected -> recordRejectedEvent()
        }
    }

    private fun recordRejectedEvent() {
        val rejected = rejectedScanEvents.incrementAndGet()
        ScannerRuntimeDiagnosticsStore.recordIngest(ScannerIngestEvent.QueueRejected)
        if (rejected == 1L || rejected % SCAN_EVENT_REJECTION_LOG_INTERVAL == 0L) {
            Log.w(TAG, "Rejected $rejected scan event(s): ingest capacity exhausted")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleBleResult(event: ScanEvent.BleResult): Boolean {
        val data = scanResultExtractor.extract(event.result)
        val params =
            io.blueeye.core.domain.repository.ScanResultParams(
                mac = data.mac,
                rssi = data.rssi,
                timestamp = event.observedAtEpochMs,
                technology = data.technology,
                name = data.name,
                manufacturerId = data.manufacturerId,
                manufacturerData = data.manufacturerData,
                manufacturerDataById = data.manufacturerDataById,
                serviceUuids = data.serviceUuids,
                serviceDataByUuid = data.serviceDataByUuid,
                appearance = data.appearance,
                txPower = data.txPower,
                isConnectable = data.isConnectable,
                primaryPhy = data.primaryPhy,
                secondaryPhy = data.secondaryPhy,
                rawData = data.rawData,
            )
        val processingResult = repository.handleScanResult(params)
        processingResult.onFailure { error ->
            val message = "Scan processing failed: ${error.message ?: error.javaClass.simpleName}"
            Log.e(TAG, message, error)
            onProcessingError(message)
        }
        return processingResult.isSuccess
    }

    private suspend fun handleClassicResult(result: ScanEvent.ClassicResult): Boolean {
        val processingResult =
            repository.handleClassicDiscovery(
                mac = result.mac,
                name = result.name,
                rssi = result.rssi,
                classOfDevice = result.classOfDevice,
                serviceUuids = result.serviceUuids,
            )
        processingResult.onFailure { error ->
            val message = "Classic scan processing failed: ${error.message ?: error.javaClass.simpleName}"
            Log.e(TAG, message, error)
            onProcessingError(message)
        }
        return processingResult.isSuccess
    }

    private companion object {
        const val TAG = "ScanIngestPipeline"
        const val SCAN_EVENT_BUFFER_CAPACITY = 4_096
        const val SCAN_EVENT_REJECTION_LOG_INTERVAL = 100L
    }
}

private fun monotonicNowMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
