package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Oras Smart Faucet decoder. Theengs: Oras_json.h Manufacturer ID: 0x0131 */
@Singleton
class OrasDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x0131 (LE "3101")
        if (manufacturerId != 0x0131 || data == null) return false

        // Data length 40 hex = 20 bytes
        return data.size == 20
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 20) return SensorData(beaconType = "Oras (Short)")

        try {
            // serial: hex 10,20 -> byte 5 to 14 (10 bytes)
            val serialBytes = data.copyOfRange(5, 15)
            val serial = String(serialBytes, Charsets.US_ASCII).trim()

            // batt: hex 6,2 -> byte 3
            val battery = data[3].toInt() and 0x7F

            return SensorData(
                batteryLevel = battery,
                beaconType = "Oras Smart Faucet",
                rawData = "Serial: $serial, Batt: $battery%",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Oras (Parse Error)")
        }
    }
}
