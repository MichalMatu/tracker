package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** EcoFlow Power Station decoder. Theengs: ECOFLOW_ADV_json.h Manufacturer ID: 0xB5B5 */
@Singleton
class EcoFlowDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0xB5B5
        if (manufacturerId != 0xB5B5) return false

        // Data length 26 bytes (52 hex chars)
        return (data?.size ?: 0) == 26
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 26) return SensorData(beaconType = "EcoFlow (Short Data)")

        try {
            // batt: offset 38 (byte 19), & 127
            val battery = data[19].toInt() and 0x7F

            // version: offset 6 (byte 3), len 3 bytes
            val modelCode = data.copyOfRange(3, 6).joinToString("") { "%02X".format(it) }
            val modelName =
                when (modelCode) {
                    "523630" -> "RIVER 2"
                    "523631" -> "RIVER 2 Max"
                    "523632" -> "RIVER 2 Pro"
                    "523635" -> "RIVER 3"
                    "523333" -> "DELTA 2"
                    "000000" -> "Off"
                    else -> "Power Station ($modelCode)"
                }

            return SensorData(
                batteryLevel = battery,
                beaconType = "EcoFlow $modelName",
                rawData = "EcoFlow: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "EcoFlow (Parse Error)")
        }
    }
}
