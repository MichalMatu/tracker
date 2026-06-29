package io.blueeye.core.data.details

import io.blueeye.core.domain.details.DeviceFocusedScanController
import io.blueeye.core.scanner.manager.BleScanner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeviceFocusedScanController
    @Inject
    constructor(
        private val bleScanner: BleScanner,
    ) : DeviceFocusedScanController {
        override fun startFocusedScan(macAddress: String): Result<Unit> =
            runCatching {
                bleScanner.startFocusedScan(macAddress)
            }

        override fun resumePassiveScan(): Result<Unit> =
            runCatching {
                bleScanner.startScanning()
            }
    }
