package io.blueeye.core.data.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.tracker.AddressCarryoverTracker
import io.blueeye.core.data.tracker.session.FollowMeSessionManager
import io.blueeye.core.domain.scanner.ScannerRuntimeController
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.service.ScannerService
import io.blueeye.service.ScannerServiceController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidScannerRuntimeController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val carryoverTracker: AddressCarryoverTracker,
        private val sessionManager: FollowMeSessionManager,
    ) : ScannerRuntimeController {
        override val scannerState: StateFlow<ScannerRuntimeState> = ScannerService.scannerState

        override fun startScanning(): Result<Unit> =
            runCatching {
                ScannerServiceController.start(context)
            }

        override fun stopScanning(): Result<Unit> =
            runCatching {
                ScannerServiceController.stop(context)
            }

        override fun resetTrackingMemory(): Result<Unit> =
            runCatching {
                carryoverTracker.clear()
                sessionManager.resetSession()
            }
    }
