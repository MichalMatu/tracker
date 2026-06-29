package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Xiaomi MiLamp (MUE4094RT) decoder. Theengs: MUE4094RT_json.h Service UUID: FE95 */
@Singleton
class XiaomiMiLampDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FE95
        if (!serviceUuids.any { it.lowercase().contains("fe95") }) return false

        val serviceData = findServiceData(rawData, 0xFE95) ?: return false

        // Product ID 0xDD30 (hex index 2: 30dd)
        if (serviceData.size < 4) return false

        return serviceData[2].toInt() == 0x30 && serviceData[3].toInt() == 0xDD.toByte().toInt()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Xiaomi MiLamp (Error)")

        val serviceData =
            findServiceData(rawData, 0xFE95)
                ?: return SensorData(beaconType = "Xiaomi MiLamp (No Data)")

        try {
            val type = serviceData[0].toInt() and 0xFF
            var motion: Boolean? = null
            var darkness: Double? = null

            if (type == 0x40) {
                motion = true
                // darkness: byte 4
                if (serviceData.size > 4) {
                    darkness = (serviceData[4].toInt() and 0xFF).toDouble()
                }
            }

            return SensorData(
                movementDetected = motion,
                illuminanceLux = darkness,
                beaconType = "Xiaomi MiLamp (MUE4094RT)",
                rawData = "MiLamp: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Xiaomi MiLamp (Parse Error)")
        }
    }

    private fun findServiceData(
        rawData: ByteArray,
        uuid16: Int,
    ): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16) {
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    val foundUuid = (uHigh shl 8) or uLow
                    if (foundUuid == uuid16) {
                        val payloadLen = len - 3
                        if (payloadLen > 0) {
                            return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
                        }
                    }
                }
            }
            i += 1 + len
        }
        return null
    }
}
