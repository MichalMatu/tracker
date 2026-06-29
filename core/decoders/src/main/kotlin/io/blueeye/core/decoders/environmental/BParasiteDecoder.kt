package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * b-parasite v1.0-1.2 plant sensor decoder. Theengs: BPARASITE_json.h Service UUID: 0x181A
 * Measures: temp, humidity, soil moisture, illuminance (optional), battery voltage
 */
@Singleton
class BParasiteDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service UUID 181A and Service Data starting with "1" or "2"
        if (!serviceUuids.any { it.lowercase().contains("181a") }) return false

        val serviceData = findServiceData181A(rawData) ?: return false

        // servicedata >= 32 hex chars = 16 bytes
        if (serviceData.size < 16) return false

        // First nibble should be 1 or 2 (protocol version)
        val firstByte = serviceData[0].toInt() and 0xFF
        val firstNibble = (firstByte shr 4) and 0x0F
        return firstNibble == 1 || firstNibble == 2
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "b-parasite (Error)")

        val serviceData =
            findServiceData181A(rawData)
                ?: return SensorData(beaconType = "b-parasite (No Data)")

        if (serviceData.size < 16) return SensorData(beaconType = "b-parasite (Short)")

        try {
            val firstByte = serviceData[0].toInt() and 0xFF
            val protocolVersion = (firstByte shr 4) and 0x0F

            // Voltage: Hex 4-7 (Bytes 2-3), BE, /1000
            val vHigh = serviceData[2].toInt() and 0xFF
            val vLow = serviceData[3].toInt() and 0xFF
            val voltageRaw = (vHigh shl 8) or vLow
            val voltage = voltageRaw / 1000.0

            // Temp: Hex 8-11 (Bytes 4-5), BE
            val tHigh = serviceData[4].toInt() and 0xFF
            val tLow = serviceData[5].toInt() and 0xFF
            val tempRaw = (tHigh shl 8) or tLow
            val temp = if (protocolVersion == 1) tempRaw / 1000.0 else tempRaw / 100.0

            // Humidity: Hex 12-15 (Bytes 6-7), /655.35
            val hHigh = serviceData[6].toInt() and 0xFF
            val hLow = serviceData[7].toInt() and 0xFF
            val humRaw = (hHigh shl 8) or hLow
            val humidity = humRaw / 655.35

            // Soil Moisture: Hex 16-19 (Bytes 8-9), /655.35
            val mHigh = serviceData[8].toInt() and 0xFF
            val mLow = serviceData[9].toInt() and 0xFF
            val moistureRaw = (mHigh shl 8) or mLow
            val moisture = moistureRaw / 655.35

            // Illuminance (optional): Hex 32-35 (Bytes 16-17) if available
            var lux: Double? = null
            val hasLux = (firstByte and 0x01) == 1 // Bit 0 of byte 0 indicates lux present
            if (hasLux && serviceData.size >= 18) {
                val lHigh = serviceData[16].toInt() and 0xFF
                val lLow = serviceData[17].toInt() and 0xFF
                lux = ((lHigh shl 8) or lLow).toDouble()
            }

            // Estimate battery from voltage (2.2V = 0%, 3.0V = 100%)
            val batteryPercent = ((voltage - 2.2) / 0.8 * 100).coerceIn(0.0, 100.0).toInt()

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                soilMoisturePercent = moisture,
                illuminanceLux = lux,
                voltageV = voltage,
                batteryLevel = batteryPercent,
                beaconType = "b-parasite v$protocolVersion",
                rawData = "BParasite: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "b-parasite (Parse Error)")
        }
    }

    private fun findServiceData181A(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16) { // Service Data 16-bit UUID
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    // 181A in LE: 1A 18
                    if (uLow == 0x1A && uHigh == 0x18) {
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
