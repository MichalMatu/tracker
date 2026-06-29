package io.blueeye.core.decoders.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic iBeacon decoder. Theengs: iBeacon_json.h Manufacturer ID: 0x004C (Apple), data format 02
 * 15
 */
@Singleton
class IBeaconDecoder
@Inject
constructor() : BleBeaconDecoder {
    override val priority: Int = -100

    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple manufacturer ID
        if (manufacturerId != 0x004C || data == null) return false

        // Check for iBeacon format: 02 15
        if (data.size < 23) return false
        return data[0] == 0x02.toByte() && data[1] == 0x15.toByte()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 23) return SensorData(beaconType = "iBeacon (Error)")

        try {
            // iBeacon layout:
            // Bytes 0-1: Type (02 15)
            // Bytes 2-17: Proximity UUID (16 bytes)
            // Bytes 18-19: Major (BE)
            // Bytes 20-21: Minor (BE)
            // Byte 22: TX Power (signed)

            // Extract UUID
            val uuidBytes = data.copyOfRange(2, 18)
            val uuid = uuidBytes.joinToString("") { "%02X".format(it) }
            val formattedUuid =
                "${uuid.substring(
                    0,
                    8,
                )}-${uuid.substring(
                    8,
                    12
                )}-${uuid.substring(12, 16)}-${uuid.substring(16, 20)}-${uuid.substring(20, 32)}"

            // Major
            val majorHigh = data[18].toInt() and 0xFF
            val majorLow = data[19].toInt() and 0xFF
            val major = (majorHigh shl 8) or majorLow

            // Minor
            val minorHigh = data[20].toInt() and 0xFF
            val minorLow = data[21].toInt() and 0xFF
            val minor = (minorHigh shl 8) or minorLow

            // TX Power
            val txPower = data[22].toInt() // signed byte

            // Check if last byte might be voltage (if bit 3 = 0)
            val lastByte = data[22].toInt() and 0xFF
            val isVoltage = (lastByte and 0x08) == 0
            val voltage = if (isVoltage && lastByte > 0) lastByte / 10.0 else null

            val statusStr = "UUID: $formattedUuid, Major: $major, Minor: $minor"

            return SensorData(
                txPower = if (!isVoltage) txPower else null,
                voltageV = voltage,
                sensorStatus = statusStr,
                beaconType = "iBeacon",
                rawData = "iBeacon: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "iBeacon (Parse Error)")
        }
    }
}
