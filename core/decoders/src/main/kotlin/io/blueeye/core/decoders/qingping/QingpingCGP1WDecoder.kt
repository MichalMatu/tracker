package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Qingping Weather Station (CGP1W) decoder. Theengs: CGP1W_json.h Service UUID: FDCD, index 2 (byte
 * 1) = 09
 */
@Singleton
class QingpingCGP1WDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service UUID FDCD
        if (!serviceUuids.any { it.lowercase().contains("fdcd") }) return false

        val serviceData = findServiceDataFDCD(rawData) ?: return false

        // Service data = 42 hex chars = 21 bytes
        if (serviceData.size != 21) return false

        // Check index 2 (byte 1) for "09"
        val typeByte = serviceData[1].toInt() and 0xFF
        return typeByte == 0x09
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Qingping CGP1W (Error)")

        val serviceData =
            findServiceDataFDCD(rawData)
                ?: return SensorData(beaconType = "Qingping CGP1W (No Data)")

        if (serviceData.size < 21) return SensorData(beaconType = "Qingping CGP1W (Short)")

        try {
            // tempc: offset 20 (byte 10), len 2 bytes, signed, /10
            val tHigh = serviceData[10].toInt() and 0xFF
            val tLow = serviceData[11].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tempRaw / 10.0

            // hum: offset 24 (byte 12), len 2 bytes, /10
            val hLow = serviceData[12].toInt() and 0xFF
            val hHigh = serviceData[13].toInt() and 0xFF
            val humRaw = ((hHigh shl 8) or hLow).toShort()
            val humidity = humRaw / 10.0

            // pres: offset 32 (byte 16), len 2 bytes, /10
            val pLow = serviceData[16].toInt() and 0xFF
            val pHigh = serviceData[17].toInt() and 0xFF
            val presRaw = ((pHigh shl 8) or pLow).toShort()
            val pressure = presRaw / 10.0

            // batt: offset 40 (byte 20), len 1 byte, & 127
            val batt = serviceData[20].toInt() and 0x7F

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity.toDouble(),
                pressureHpa = pressure,
                batteryLevel = batt,
                beaconType = "Qingping Weather Station (CGP1W)",
                rawData = "CGP1W: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Qingping CGP1W (Parse Error)")
        }
    }

    private fun findServiceDataFDCD(rawData: ByteArray): ByteArray? {
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
                    // FDCD in LE: CD FD
                    if (uLow == 0xCD && uHigh == 0xFD) {
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
