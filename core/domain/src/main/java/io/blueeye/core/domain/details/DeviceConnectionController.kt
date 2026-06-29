package io.blueeye.core.domain.details

import io.blueeye.core.model.DeviceConnectionState
import io.blueeye.core.model.GattService
import kotlinx.coroutines.flow.StateFlow

interface DeviceConnectionController {
    val connectionState: StateFlow<DeviceConnectionState>

    val services: StateFlow<List<GattService>>

    fun connect(
        macAddress: String,
        recordFingerprint: String = macAddress,
    ): Result<Unit>

    fun disconnect(): Result<Unit>
}
