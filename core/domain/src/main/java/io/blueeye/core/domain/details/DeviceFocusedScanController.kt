package io.blueeye.core.domain.details

interface DeviceFocusedScanController {
    fun startFocusedScan(macAddress: String): Result<Unit>

    fun resumePassiveScan(): Result<Unit>
}
