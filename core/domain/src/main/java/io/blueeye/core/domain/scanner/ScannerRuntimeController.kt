package io.blueeye.core.domain.scanner

import kotlinx.coroutines.flow.StateFlow

interface ScannerRuntimeController {
    val scannerState: StateFlow<ScannerRuntimeState>

    fun startScanning(): Result<Unit>

    fun stopScanning(): Result<Unit>

    fun resetTrackingMemory(): Result<Unit>
}
