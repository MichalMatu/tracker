package io.blueeye.core.domain.publicsafety

import io.blueeye.core.model.PublicSafetySignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PublicSafetySignalMonitor {
    val detectionEnabled: Flow<Boolean>
    val activeSignalCount: StateFlow<Int>
    val activeSignals: Flow<List<PublicSafetySignal>>

    suspend fun setDetectionEnabled(enabled: Boolean): Result<Unit>

    fun clearActiveSignals(): Result<Unit>
}
