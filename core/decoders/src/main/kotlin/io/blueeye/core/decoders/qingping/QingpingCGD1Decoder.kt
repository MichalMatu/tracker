package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClearGrass/Qingping Alarm Clock (CGC1/CGD1) decoder. Theengs: CGD1_json.h Service UUID: FDCD
 * Service data = 34 hex chars, index 2 = "0c" or "1e"
 */
@Singleton
class QingpingCGD1Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service UUID FDCD
        if (!serviceUuids.any { it.lowercase().contains("fdcd") }) return false

        val serviceData = findServiceDataFDCD(rawData) ?: return false

        // Service data = 34 hex chars = 17 bytes
        if (serviceData.size < 17) return false

        // Check index 2 (byte 1) for "0c" or "1e"
        val typeByte = serviceData[1].toInt() and 0xFF
        return typeByte == 0x0C || typeByte == 0x1E
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Qingping CGD1 (Error)")

        val serviceData =
            findServiceDataFDCD(rawData)
                ?: return SensorData(beaconType = "Qingping CGD1 (No Data)")

        if (serviceData.size < 17) return SensorData(beaconType = "Qingping CGD1 (Short)")

        try {
            // Temp: Hex 20-23 (Bytes 10-11), Signed, BE, /10
            val tHigh = serviceData[10].toInt() and 0xFF
            val tLow = serviceData[11].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tempRaw / 10.0

            // Humidity: Hex 24-27 (Bytes 12-13), Signed, LE, /10
            val hLow = serviceData[12].toInt() and 0xFF
            val hHigh = serviceData[13].toInt() and 0xFF
            val humRaw = ((hHigh shl 8) or hLow).toShort()
            val humidity = humRaw / 10.0

            // Battery: Hex 32-33 (Byte 16), & 127
            val battRaw = serviceData[16].toInt() and 0xFF
            val batt = battRaw and 0x7F

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity.toDouble(),
                batteryLevel = batt,
                beaconType = "Qingping Alarm Clock (CGD1)",
                rawData = "CGD1: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Qingping CGD1 (Parse Error)")
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
