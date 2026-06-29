package io.blueeye.core.domain.usecase

import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.model.Device
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetScannedDevicesUseCase
    @Inject
    constructor(
        private val deviceRepository: DeviceRepository,
    ) {
        private companion object {
            const val MILLIS_PER_SECOND = 1000L
            const val REFRESH_INTERVAL_MS = 10000L // Refresh window every 10s
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(sinceSecondsAgo: Long? = null): Flow<Result<List<Device>>> {
            if (sinceSecondsAgo == null) {
                return deviceRepository.getAllDevices()
            }

            // Emit a sliding timestamp every 10 seconds to refresh the database query window.
            // This ensures devices disappear from the list after the retention period,
            // even if no new scan results are being received.
            return flow {
                while (true) {
                    emit(System.currentTimeMillis() - (sinceSecondsAgo * MILLIS_PER_SECOND))
                    delay(REFRESH_INTERVAL_MS)
                }
            }
                .distinctUntilChanged()
                .flatMapLatest { sinceTimestamp ->
                    deviceRepository.getRecentDevices(sinceTimestamp)
                }
        }
    }
