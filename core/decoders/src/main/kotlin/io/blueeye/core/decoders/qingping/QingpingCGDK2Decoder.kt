package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Qingping TH Lite (CGDK2) decoder. Theengs: CGDK2_json.h Service UUID: FDCD, index 2 = "10" */
@Singleton
class QingpingCGDK2Decoder
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

        // Check index 2 (byte 1) for "10"
        val typeByte = serviceData[1].toInt() and 0xFF
        return typeByte == 0x10
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Qingping CGDK2 (Error)")

        val serviceData =
            findServiceDataFDCD(rawData)
                ?: return SensorData(beaconType = "Qingping CGDK2 (No Data)")

        if (serviceData.size < 17) return SensorData(beaconType = "Qingping CGDK2 (Short)")

        try {
            // Temp: Hex 20-23 (Bytes 10-11), Signed, /10
            val tHigh = serviceData[10].toInt() and 0xFF
            val tLow = serviceData[11].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tempRaw / 10.0

            // Humidity: Hex 24-27 (Bytes 12-13), Signed, LE, /10
            val hLow = serviceData[12].toInt() and 0xFF
            val hHigh = serviceData[13].toInt() and 0xFF
            val humRaw = ((hHigh shl 8) or hLow).toShort()
            val humidity = humRaw / 10.0

            // Battery: Hex 32-33 (Byte 16)
            val batt = serviceData[16].toInt() and 0xFF

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity.toDouble(),
                batteryLevel = batt,
                beaconType = "Qingping TH Lite (CGDK2)",
                rawData = "CGDK2: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Qingping CGDK2 (Parse Error)")
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
