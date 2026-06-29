package io.blueeye.core.decoders.govee

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govee Bluetooth BBQ Thermometer (H5055) decoder. Supports up to 6 temperature probes. Theengs:
 * H5055_json.h Manufacturer ID: Usually 0xEC88 (Govee)
 */
@Singleton
class GoveeH5055Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Must be Govee manufacturer ID (0xEC88) to avoid false positives
        if (manufacturerId != 0xEC88 && manufacturerId != 0x0001) return false

        val mData = data ?: return false
        if (mData.size < 7) return false

        val typeByte = mData[6].toInt() and 0xFF
        return (typeByte == 0x06 || typeByte == 0x20 || typeByte == 0x22) &&
            (mData.size == 22 || mData.size == 20)
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 7) return SensorData(beaconType = "Govee H5055 (Short)")

        try {
            // battery: byte 4 (offset 8 hex)
            val batt = data[4].toInt() and 0xFF

            // Probe indices switching: byte 5 (offset 10 hex)
            val switchByte = data[5].toInt() and 0xFF
            val bit3 = (switchByte shr 3) and 0x01
            val bit2 = (switchByte shr 2) and 0x01

            val temps = mutableMapOf<Int, Double>()

            // Each temp is 2 bytes (LE in Theengs? No, value_from_hex_data 14, 4 usually means 2
            // bytes BE/LE?)
            // Looking at H5072 (another Govee), they use a specific format.
            // Here: 14, 4, true, false -> byte 7,8 signed.

            fun getTemp(offset: Int): Double? {
                if (offset + 1 >= data.size) return null
                val low = data[offset].toInt() and 0xFF
                val high = data[offset + 1].toInt() and 0xFF
                if (low == 0xFF && high == 0xFF) return null
                val raw = ((high shl 8) or low).toShort()
                return raw / 10.0
            }

            if (bit3 == 0 && bit2 == 0) {
                getTemp(7)?.let { temps[1] = it }
                getTemp(14)?.let { temps[2] = it }
            } else if (bit3 == 0 && bit2 == 1) {
                getTemp(7)?.let { temps[3] = it }
                getTemp(14)?.let { temps[4] = it }
            } else if (bit3 == 1 && bit2 == 0) {
                getTemp(7)?.let { temps[5] = it }
                getTemp(14)?.let { temps[6] = it }
            }

            val tempStr = temps.entries.joinToString(", ") { "P${it.key}: ${it.value}°C" }

            return SensorData(
                temperatureCelcius =
                temps[1]
                    ?: temps[2] ?: temps[3] ?: temps[4] ?: temps[5] ?: temps[6],
                batteryLevel = batt,
                beaconType = "Govee H5055 BBQ Thermometer",
                rawData = tempStr,
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Govee H5055 (Parse Error)")
        }
    }
}
