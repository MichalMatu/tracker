package io.blueeye.core.domain.scanner

enum class ScannerLifecycleTransition {
    SERVICE_CREATED,
    START_REQUESTED,
    START_IGNORED_ALREADY_ACTIVE,
    RUNNING,
    FOCUSED_SCAN_REQUESTED,
    PASSIVE_SCAN_RESUMED,
    STOP_REQUESTED,
    STOP_IGNORED_ALREADY_IDLE,
    BLUETOOTH_OFF,
    START_FAILED,
    SCANNER_FAILED,
    DESTROYED,
}

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
    val lastLifecycleTransition: ScannerLifecycleTransition? = null,
    val lastLifecycleTransitionAt: Long? = null,
    val lifecycleTransitionCount: Long = 0L,
    val updatedAt: Long = 0L,
)
