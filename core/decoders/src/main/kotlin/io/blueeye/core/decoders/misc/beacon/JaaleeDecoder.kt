package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Jaalee TH sensor (F525/F51C) decoder. Theengs: JAALEE_json.h */
@Singleton
class JaaleeDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val data = input.data
        if (data == null) return false

        // UUID contain f525 or f51c
        val uuidMatches =
            serviceUuids.any {
                it.lowercase().contains("f525") || it.lowercase().contains("f51c")
            }

        // manufacturerdata length = 52 hex chars = 26 bytes
        return uuidMatches && data.size == 26
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 26) return SensorData(beaconType = "Jaalee (Short Data)")

        try {
            // tempc: offset 40 hex (byte 20), 2 bytes BE, unsigned
            val tHigh = data[20].toInt() and 0xFF
            val tLow = data[21].toInt() and 0xFF
            val tRaw = (tHigh shl 8) or tLow
            val temp = (tRaw * 175.0 / 65535.0) - 45.0

            // hum: offset 44 hex (byte 22), 2 bytes BE, unsigned
            val hHigh = data[22].toInt() and 0xFF
            val hLow = data[23].toInt() and 0xFF
            val hRaw = (hHigh shl 8) or hLow
            val hum = (hRaw * 100.0 / 65535.0)

            // batt: offset 50 hex (byte 25), 1 byte unsigned
            val battery = data[25].toInt() and 0xFF

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = battery,
                beaconType = "Jaalee TH Sensor",
                rawData = "Jaalee: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Jaalee (Parse Error)")
        }
    }
}
