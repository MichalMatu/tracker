package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Qingping Air Monitor Lite (CGDN1) decoder. Theengs: CGDN1_json.h Service UUID: FDCD, measures
 * temp, humidity, PM2.5, PM10, CO2
 */
@Singleton
class QingpingCGDN1Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        if (!serviceUuids.any { it.lowercase().contains("fdcd") }) return false

        val serviceData = findServiceDataFDCD(rawData) ?: return false

        // Service data = 48 hex chars = 24 bytes
        if (serviceData.size < 24) return false

        // Check index 2 for 0x0e or 0x24
        val typeByte = serviceData[1].toInt() and 0xFF
        return typeByte == 0x0E || typeByte == 0x24
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "CGDN1 (Error)")

        val serviceData =
            findServiceDataFDCD(rawData) ?: return SensorData(beaconType = "CGDN1 (No Data)")

        if (serviceData.size < 24) return SensorData(beaconType = "CGDN1 (Short)")

        try {
            // Temperature: Hex 20-23 (Bytes 10-11), LE, /10
            val tLow = serviceData[10].toInt() and 0xFF
            val tHigh = serviceData[11].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tempRaw / 10.0

            // Humidity: Hex 24-27 (Bytes 12-13), LE, /10
            val hLow = serviceData[12].toInt() and 0xFF
            val hHigh = serviceData[13].toInt() and 0xFF
            val humRaw = ((hHigh shl 8) or hLow).toShort()
            val humidity = humRaw / 10.0

            // PM2.5: Hex 32-35 (Bytes 16-17), LE
            val pm25Low = serviceData[16].toInt() and 0xFF
            val pm25High = serviceData[17].toInt() and 0xFF
            val pm25 = ((pm25High shl 8) or pm25Low).toShort().toInt()

            // PM10: Hex 36-39 (Bytes 18-19), LE
            val pm10Low = serviceData[18].toInt() and 0xFF
            val pm10High = serviceData[19].toInt() and 0xFF
            val pm10 = ((pm10High shl 8) or pm10Low).toShort().toInt()

            // CO2: Hex 44-47 (Bytes 22-23), LE
            val co2Low = serviceData[22].toInt() and 0xFF
            val co2High = serviceData[23].toInt() and 0xFF
            val co2 = ((co2High shl 8) or co2Low).toShort().toInt()

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity.toDouble(),
                pm25Ugm3 = pm25,
                pm10Ugm3 = pm10,
                co2Ppm = co2,
                beaconType = "Qingping Air Monitor (CGDN1)",
                rawData = "CGDN1: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "CGDN1 (Parse Error)")
        }
    }

    private fun findServiceDataFDCD(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16 && i + 3 < rawData.size) {
                val uLow = rawData[i + 2].toInt() and 0xFF
                val uHigh = rawData[i + 3].toInt() and 0xFF
                if (uLow == 0xCD && uHigh == 0xFD) {
                    val payloadLen = len - 3
                    if (payloadLen > 0) {
                        return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
                    }
                }
            }
            i += 1 + len
        }
        return null
    }
}
