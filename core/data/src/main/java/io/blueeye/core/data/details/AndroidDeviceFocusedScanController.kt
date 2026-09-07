package io.blueeye.core.data.details

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.domain.details.DeviceFocusedScanController
import io.blueeye.service.ScannerServiceController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeviceFocusedScanController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceFocusedScanController {
        override fun startFocusedScan(macAddress: String): Result<Unit> =
            ScannerServiceController.startFocusedScan(context, macAddress)

        override fun resumePassiveScan(): Result<Unit> =
            ScannerServiceController.resumePassiveScan(context)
    }
