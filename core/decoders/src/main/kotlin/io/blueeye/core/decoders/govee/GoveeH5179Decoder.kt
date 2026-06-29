package io.blueeye.core.decoders.govee

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govee Thermo-Hygrometer (H5179) decoder. Supports standard Govee (0xEC88) and Govee BTH (0x0001)
 * formats. Theengs: H5179_json.h
 */
@Singleton
class GoveeH5179Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (data == null) return false

        // Standard H5179: Mfg ID 0xEC88, data len 11
        if (manufacturerId == 0xEC88 && data.size == 11) return true

        // H5179_N: Mfg ID 0x0001, data len >= 8
        if (manufacturerId == 0x0001 && data.size >= 8) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val manufacturerId = input.manufacturerId
        val data = input.data ?: byteArrayOf()
        return when (manufacturerId) {
            0xEC88 -> decodeStandard(data)
            0x0001 -> decodeBTH(data)
            else -> SensorData(beaconType = "Govee H5179 (Unknown Format)")
        }
    }

    private fun decodeStandard(data: ByteArray): SensorData {
        if (data.size < 11) return SensorData(beaconType = "Govee H5179 (Short)")

        // data starts at byte 0 after ID
        // temp: byte 6, 2 bytes, signed, /100
        val tLow = data[6].toInt() and 0xFF
        val tHigh = data[7].toInt() and 0xFF
        val temp = ((tHigh shl 8) or tLow).toShort() / 100.0

        // hum: byte 8, 2 bytes, signed, /100
        val hLow = data[8].toInt() and 0xFF
        val hHigh = data[9].toInt() and 0xFF
        val hum = ((hHigh shl 8) or hLow).toShort() / 100.0

        // batt: byte 10
        val batt = data[10].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = hum,
            batteryLevel = batt,
            beaconType = "Govee H5179",
            rawData = "H5179 Std: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodeBTH(data: ByteArray): SensorData {
        if (data.size < 8) return SensorData(beaconType = "Govee H5179 (Short)")

        // BTH format: Byte 4-6 is 3-byte combined value
        val b1 = data[4].toInt() and 0xFF
        val b2 = data[5].toInt() and 0xFF
        val b3 = data[6].toInt() and 0xFF
        val rawValue = (b1 shl 16) or (b2 shl 8) or b3

        val isNegative = (rawValue and 0x800000) != 0
        val tempValue = rawValue and 0x7FFFFF
        val temp = if (isNegative) -(tempValue / 10000.0) else (rawValue / 10000.0)
        val humidity = (tempValue % 1000) / 10.0
        val batt = data[7].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity,
            batteryLevel = batt,
            beaconType = "Govee H5179 (BTH)",
            rawData = "H5179 BTH: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }
}
