package io.blueeye.core.data.details

import io.blueeye.core.data.classifier.BeaconDecoderManager
import io.blueeye.core.domain.details.DeviceSensorDataDecoder
import io.blueeye.core.model.Device
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSensorDataDecoderImpl
    @Inject
    constructor(
        private val beaconDecoderManager: BeaconDecoderManager,
    ) : DeviceSensorDataDecoder {
        override fun decode(device: Device): Result<SensorData?> =
            runCatching {
                val rawHex = device.lastRawData ?: return@runCatching null
                val rawBytes = rawHex.toByteArrayFromHex()
                beaconDecoderManager.decode(
                    mac = device.macAddress,
                    manufacturerRecords = emptyMap(),
                    serviceUuids = emptyList(),
                    rawData = rawBytes,
                )
            }

        private fun String.toByteArrayFromHex(): ByteArray {
            val cleanHex = replace(" ", "").replace(":", "")
            return ByteArray(cleanHex.length / HEX_CHARS_PER_BYTE) { index ->
                cleanHex.substring(
                    index * HEX_CHARS_PER_BYTE,
                    index * HEX_CHARS_PER_BYTE + HEX_CHARS_PER_BYTE,
                ).toInt(HEX_RADIX).toByte()
            }
        }

        private companion object {
            private const val HEX_CHARS_PER_BYTE = 2
            private const val HEX_RADIX = 16
        }
    }
