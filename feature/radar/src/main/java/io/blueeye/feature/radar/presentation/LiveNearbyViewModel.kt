package io.blueeye.feature.radar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.blueeye.core.domain.scanner.ScannerRuntimeController
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.domain.usecase.GetRadarDevicesUseCase
import io.blueeye.core.model.RadarDeviceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LiveNearbyViewModel
    @Inject
    constructor(
        getRadarDevicesUseCase: GetRadarDevicesUseCase,
        private val scannerRuntimeController: ScannerRuntimeController,
    ) : ViewModel() {
        val scannerState: StateFlow<ScannerRuntimeState> = scannerRuntimeController.scannerState

        private val pinnedFingerprint = MutableStateFlow<String?>(null)
        private val orderFrozen = MutableStateFlow(false)
        private val signalHistory = LiveNearbySignalHistory()
        private val orderController = LiveNearbyOrderController()

        private val clock =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(UI_TICK_MS)
                }
            }

        val uiState: StateFlow<LiveNearbyUiState> =
            combine(
                getRadarDevicesUseCase(sinceSecondsAgo = SOURCE_WINDOW_SECONDS),
                clock,
                pinnedFingerprint,
                orderFrozen,
            ) { result, nowMs, pinned, frozen ->
                result.fold(
                    onSuccess = { summaries ->
                        signalHistory.observe(summaries, nowMs)
                        val devices = summaries.map { it.toLiveNearbyDevice(nowMs) }
                        val assembled = LiveNearbyAssembler.assemble(devices, pinned)
                        LiveNearbyUiState.Success(orderController.apply(assembled, nowMs, frozen))
                    },
                    onFailure = { error ->
                        LiveNearbyUiState.Error(error.message ?: "Unable to load nearby signals")
                    },
                )
            }
                .flowOn(Dispatchers.Default)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = LiveNearbyUiState.Loading,
                )

        fun toggleScanning() {
            when (scannerState.value) {
                ScannerRuntimeState.Running,
                ScannerRuntimeState.Starting,
                -> scannerRuntimeController.stopScanning()
                ScannerRuntimeState.Idle,
                is ScannerRuntimeState.Error,
                -> scannerRuntimeController.startScanning()
            }
        }

        fun togglePin(fingerprint: String) {
            pinnedFingerprint.value =
                if (pinnedFingerprint.value == fingerprint) null else fingerprint
        }

        fun setOrderFrozen(frozen: Boolean) {
            orderFrozen.value = frozen
        }

        private fun RadarDeviceSummary.toLiveNearbyDevice(nowMs: Long): LiveNearbyDevice {
            val ageMs = (nowMs - lastSeenAt).coerceAtLeast(0L)
            val freshness =
                when {
                    ageMs <= ACTIVE_WINDOW_MS -> LiveNearbyFreshness.ACTIVE
                    ageMs <= RECENT_WINDOW_MS -> LiveNearbyFreshness.RECENT
                    else -> LiveNearbyFreshness.STALE
                }
            return LiveNearbyDevice(
                item = RadarUiMapper.mapToUi(this, isNew = false, activeProbeMac = null),
                smoothedRssi = signalHistory.smoothedRssi(this),
                trend = signalHistory.trend(fingerprint, nowMs),
                freshness = freshness,
                ageMs = ageMs,
            )
        }

        private companion object {
            const val SOURCE_WINDOW_SECONDS = 180L
            const val UI_TICK_MS = 1_000L
            const val ACTIVE_WINDOW_MS = 15_000L
            const val RECENT_WINDOW_MS = 60_000L
        }
    }
