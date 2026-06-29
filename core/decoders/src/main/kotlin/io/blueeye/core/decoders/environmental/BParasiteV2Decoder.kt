package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * b-parasite v2.0 plant sensor decoder (BTHome format). Theengs: BPARASITEV2_json.h Name contains
 * "prst", UUID contains FCD2
 */
@Singleton
class BParasiteV2Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for UUID containing FCD2 (BTHome)
        if (!serviceUuids.any { it.lowercase().contains("fcd2") }) return false

        // Check for local name containing "prst"
        val localName = extractLocalName(rawData)
        return localName?.lowercase()?.contains("prst") == true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "b-parasite v2 (Error)")

        val serviceData =
            findServiceDataFCD2(rawData)
                ?: return SensorData(beaconType = "b-parasite v2 (No Data)")

        try {
            // BTHome format - parse TLV-style data
            var temp: Double? = null
            var humidity: Double? = null
            var moisture: Double? = null
            var lux: Double? = null
            var battery: Int? = null
            var voltage: Double? = null

            var i = 0
            while (i < serviceData.size - 1) {
                val typeId = serviceData[i].toInt() and 0xFF
                i++

                when (typeId) {
                    0x01 -> { // Battery (1 byte)
                        if (i < serviceData.size) {
                            battery = serviceData[i].toInt() and 0xFF
                            i += 1
                        }
                    }
                    0x02 -> { // Temperature (2 bytes, signed, BE, /100)
                        if (i + 1 < serviceData.size) {
                            val tHigh = serviceData[i].toInt() and 0xFF
                            val tLow = serviceData[i + 1].toInt() and 0xFF
                            val tRaw = ((tHigh shl 8) or tLow).toShort()
                            temp = tRaw / 100.0
                            i += 2
                        }
                    }
                    0x05 -> { // Illuminance (3 bytes, LE, /100)
                        if (i + 2 < serviceData.size) {
                            val l1 = serviceData[i].toLong() and 0xFF
                            val l2 = serviceData[i + 1].toLong() and 0xFF
                            val l3 = serviceData[i + 2].toLong() and 0xFF
                            val luxRaw = (l3 shl 16) or (l2 shl 8) or l1
                            lux = luxRaw / 100.0
                            i += 3
                        }
                    }
                    0x0C -> { // Voltage (2 bytes, LE, /1000)
                        if (i + 1 < serviceData.size) {
                            val vLow = serviceData[i].toInt() and 0xFF
                            val vHigh = serviceData[i + 1].toInt() and 0xFF
                            val vRaw = (vHigh shl 8) or vLow
                            voltage = vRaw / 1000.0
                            i += 2
                        }
                    }
                    0x2E -> { // Humidity (1 byte)
                        if (i < serviceData.size) {
                            humidity = (serviceData[i].toInt() and 0xFF).toDouble()
                            i += 1
                        }
                    }
                    0x2F -> { // Soil Moisture (1 byte)
                        if (i < serviceData.size) {
                            moisture = (serviceData[i].toInt() and 0xFF).toDouble()
                            i += 1
                        }
                    }
                    else -> {
                        // Unknown type, try to skip
                        i += 1
                    }
                }
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                soilMoisturePercent = moisture,
                illuminanceLux = lux,
                voltageV = voltage,
                batteryLevel = battery,
                beaconType = "b-parasite v2 (BTHome)",
                rawData = "BParasiteV2: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "b-parasite v2 (Parse Error)")
        }
    }

    private fun extractLocalName(rawData: ByteArray): String? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            // 0x08 = Shortened Local Name, 0x09 = Complete Local Name
            if (type == 0x08 || type == 0x09) {
                val nameBytes = rawData.copyOfRange(i + 2, i + 1 + len)
                return String(nameBytes, Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }

    private fun findServiceDataFCD2(rawData: ByteArray): ByteArray? {
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
                    // FCD2 in LE: D2 FC
                    if (uLow == 0xD2 && uHigh == 0xFC) {
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
