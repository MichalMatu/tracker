package io.blueeye.core.domain.details

import io.blueeye.core.model.Device
import io.blueeye.core.model.GattService

interface DeviceServiceResolver {
    fun resolvePersistedServices(device: Device): Result<List<GattService>>
}
