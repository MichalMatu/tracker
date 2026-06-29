package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Atomax Skale I/II decoder. Theengs: Skale_json.h */
@Singleton
class SkaleDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x81EF (LE "ef81")
        return manufacturerId == 0x81EF && data != null && data.size >= 8
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // weight: hex 4,4 (after ID) -> byte 2-3 (if indexing hex chars)
            // Theengs: manufacturerdata, 4, 4
            // Byte 0-1: ID, Byte 2-3: ??, Byte 4-5: weight?
            // Wait, hex 4,4 means start at 5th hex char (3rd byte).
            val wHigh = data[2].toInt() and 0xFF
            val wLow = data[3].toInt() and 0xFF
            val weight = ((wHigh shl 8) or wLow).toShort() / 10.0

            return SensorData(
                beaconType = "Skale Coffee Scale",
                sensorStatus = "Weight: %.1f g".format(weight),
                rawData = "Skale: %.1f g".format(weight),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Skale (Parse Error)")
        }
    }
}
