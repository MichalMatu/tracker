package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** iNode Energy Meter decoder. Theengs: iNodeEM_json.h */
@Singleton
class INodeDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (data == null || data.size < 24) return false

        // After ID (2 bytes):
        // hex 0 of MD is index 0. Here data[0] is index 0 of payload?
        // No, manufacturerdata starts with ID.
        // Wait, if manufacturerId is extracted, 'data' is the REST.
        // Theengs: "manufacturerdata", "index", 0, "90" means the WHOLE MD starts with 0x90...
        // If ID is 0x90... then manufacturerId is 0x??90 or similar.
        // Actually, many Chinese/Polish sensors use 0xFFFF or similar and encode info in MD.

        // Let's check the whole MD from rawData to be sure.
        val md = extractManufacturerData(rawData!!) ?: return false
        if (md.size != 26) return false

        val first = md[0].toInt() and 0xFF
        val third = md[2].toInt() and 0xFF

        val isPrefixValid = first == 0x90 || first == 0x92 || first == 0x94 || first == 0x96
        return isPrefixValid && third == 0x82
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        val md =
            extractManufacturerData(rawData!!)
                ?: return SensorData(beaconType = "iNode (Error)")

        try {
            // .cal: byte 16-19 & 16383
            val calRaw = extractInt(md, 16, 4)
            val cal = calRaw and 16383

            // avg: byte 4-7 * 60 / .cal
            val avgRaw = extractInt(md, 4, 4)
            val avg = if (cal > 0) (avgRaw.toDouble() * 60.0 / cal) else 0.0

            // sum: byte 8-11 / .cal
            val sumRaw = extractInt(md, 8, 4)
            val sum = if (cal > 0) (sumRaw.toDouble() / cal) else 0.0

            // units: byte 18 bit 0 -> 0:kW/kWh, 1:m3
            val unitBit = (md[18].toInt() and 0x01) != 0
            val pUnit = if (unitBit) "m³" else "kW"
            val eUnit = if (unitBit) "m³" else "kWh"

            // batt: byte 20 -> (val - 1) * 10
            val bRaw = md[20].toInt() and 0xFF
            val battery = if (bRaw in 0x01..0x0A) (bRaw - 1) * 10 else 100

            return SensorData(
                batteryLevel = battery,
                sensorStatus = "Avg: %.2f %s, Total: %.2f %s".format(avg, pUnit, sum, eUnit),
                beaconType = "iNode Energy Meter",
                rawData = "iNode: %.2f %s".format(avg, pUnit),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "iNode (Parse Error)")
        }
    }

    private fun extractInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            if (start + i < data.size) {
                res = (res shl 8) or (data[start + i].toInt() and 0xFF)
            }
        }
        return res
    }

    private fun extractManufacturerData(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0xFF) {
                return rawData.copyOfRange(i + 2, i + 1 + len)
            }
            i += 1 + len
        }
        return null
    }
}
