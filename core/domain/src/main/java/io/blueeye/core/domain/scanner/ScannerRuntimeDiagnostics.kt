package io.blueeye.core.domain.scanner

data class ScannerRuntimeDiagnostics(
    val runtimeProfile: ScannerRuntimeProfile = ScannerRuntimePolicy.profile,
    val state: ScannerRuntimeState = ScannerRuntimeState.Idle,
    val startedAt: Long? = null,
    val lastBleResultAt: Long? = null,
    val lastBleMac: String? = null,
    val lastClassicResultAt: Long? = null,
    val lastClassicMac: String? = null,
    val bleResultsPerMinute: Int = 0,
    val classicResultsPerMinute: Int = 0,
    val droppedQueueEvents: Long = 0L,
    val lastScanError: String? = null,
    val updatedAt: Long = 0L,
)
