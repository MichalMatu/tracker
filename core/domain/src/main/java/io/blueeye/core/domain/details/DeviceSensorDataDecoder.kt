package io.blueeye.core.domain.details

import io.blueeye.core.model.Device
import io.blueeye.core.model.SensorData

interface DeviceSensorDataDecoder {
    fun decode(device: Device): Result<SensorData?>
}
