package io.blueeye.core.domain.usecase

import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.model.RadarDeviceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetRadarDevicesUseCase
    @Inject
    constructor(
        private val deviceRepository: DeviceRepository,
    ) {
        private companion object {
            const val MILLIS_PER_SECOND = 1000L
            const val REFRESH_INTERVAL_MS = 10000L
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(sinceSecondsAgo: Long): Flow<Result<List<RadarDeviceSummary>>> {
            return flow {
                while (true) {
                    emit(System.currentTimeMillis() - (sinceSecondsAgo * MILLIS_PER_SECOND))
                    delay(REFRESH_INTERVAL_MS)
                }
            }
                .distinctUntilChanged()
                .flatMapLatest { sinceTimestamp ->
                    deviceRepository.getRecentRadarDevices(sinceTimestamp)
                }
        }
    }
