package io.blueeye.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.blueeye.core.domain.publicsafety.PublicSafetySignalMonitor
import io.blueeye.core.domain.repository.WatchlistDeviceItem
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.domain.watchlist.WatchlistRangePolicy
import io.blueeye.core.model.PublicSafetySignal
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel
    @Inject
    constructor(
        private val watchlistRepository: WatchlistRepository,
        private val publicSafetySignalMonitor: PublicSafetySignalMonitor,
    ) : ViewModel() {
        private val statusRefreshTicks =
            flow {
                emit(System.currentTimeMillis())
                while (true) {
                    delay(STATUS_REFRESH_INTERVAL_MS)
                    emit(System.currentTimeMillis())
                }
            }

        /**
         * Whether public-safety signal review is enabled.
         */
        val publicSafetySignalReviewEnabled: StateFlow<Boolean> =
            publicSafetySignalMonitor
                .detectionEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        /**
         * Count of detected public-safety-style signals from the domain monitor.
         */
        val publicSafetySignalCount: StateFlow<Int> = publicSafetySignalMonitor.activeSignalCount

        /**
         * Active public-safety-style signal hints for the detailed list.
         * Throttled to prevent UI jitter/spam.
         */
        @OptIn(FlowPreview::class)
        val publicSafetySignals =
            publicSafetySignalMonitor.activeSignals
                .sample(1000L)

        /**
         * Throttled flow of watchlist devices to prevent UI flickering.
         */
        @OptIn(FlowPreview::class)
        private val throttledDevicesFlow =
            watchlistRepository.watchlistDevicesFlow
                .sample(1500L) // Update UI max every 1.5 seconds

        /**
         * Main UI state combining watchlist devices with public-safety signal review status.
         */
        @OptIn(FlowPreview::class)
        val uiState: StateFlow<WatchlistUiState> =
            combine(
                throttledDevicesFlow,
                statusRefreshTicks,
                publicSafetySignalReviewEnabled,
                publicSafetySignalCount,
                publicSafetySignals
            ) { result, now, signalReviewEnabled, signalCount, signals ->
                result.fold(
                    onSuccess = { devices ->
                        WatchlistUiState.Success(
                            watchedDevices = devices.withFreshRangeStatus(now),
                            statusUpdatedAt = now,
                            publicSafetySignalReviewEnabled = signalReviewEnabled,
                            publicSafetySignalCount = signalCount,
                            publicSafetySignals = signals
                        )
                    },
                    onFailure = { error ->
                        WatchlistUiState.Error(error.message ?: "Unknown error")
                    }
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = WatchlistUiState.Loading
            )

        /**
         * Toggle public-safety signal review on/off.
         */
        fun togglePublicSafetySignalReview() {
            viewModelScope.launch {
                val current = publicSafetySignalReviewEnabled.value
                publicSafetySignalMonitor.setDetectionEnabled(!current)

                // Clear signal hints when disabling review.
                if (current) {
                    publicSafetySignalMonitor.clearActiveSignals()
                }
            }
        }

        /**
         * Remove a device from the watchlist.
         */
        fun removeFromWatchlist(fingerprint: String) {
            viewModelScope.launch {
                watchlistRepository.removeFromWatchlist(fingerprint)
            }
        }

        /**
         * Toggle tracking enabled for a specific device.
         */
        fun setTrackingEnabled(
            fingerprint: String,
            enabled: Boolean
        ) {
            viewModelScope.launch {
                watchlistRepository.setTrackingEnabled(fingerprint, enabled)
            }
        }

        private fun List<WatchlistDeviceItem>.withFreshRangeStatus(now: Long): List<WatchlistDeviceItem> =
            map { item ->
                item.copy(
                    isInRange =
                        WatchlistRangePolicy.isInRange(
                            lastSeenAt = item.device.lastSeenAt,
                            now = now,
                        ),
                )
            }

        private companion object {
            private const val STATUS_REFRESH_INTERVAL_MS = 15_000L
        }
    }

sealed class WatchlistUiState {
    object Loading : WatchlistUiState()

    data class Error(val message: String) : WatchlistUiState()

    data class Success(
        val watchedDevices: List<WatchlistDeviceItem>,
        val statusUpdatedAt: Long,
        val publicSafetySignalReviewEnabled: Boolean,
        val publicSafetySignalCount: Int,
        val publicSafetySignals: List<PublicSafetySignal> = emptyList()
    ) : WatchlistUiState()
}
