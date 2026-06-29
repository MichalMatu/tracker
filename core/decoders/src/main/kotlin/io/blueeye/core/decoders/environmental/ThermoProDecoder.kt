package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ThermoPro TP350 / TP35x / TP393 Decoder.
 * Based on Theengs TPTH_json.h.
 */
@Singleton
class ThermoProDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        // Check for Local Name starting with TP35x
        val name = rawData?.let { extractLocalName(it) } ?: return false
        if (!name.startsWith("TP3")) return false // Optimization

        val validNames = listOf("TP350", "TP357", "TP358", "TP359", "TP393")
        if (!validNames.any { name.startsWith(it) }) return false

        // Theengs condition: manufacturerdata index 0 is C2.
        // Android ScanResult.manufacturerSpecificData only populated if ID recognized?
        // If manufacturerId is NOT null, 'data' is payload.
        // If manufacturerId is NULL, 'data' is null.
        // But we can check rawData for pattern.

        // If ID matches, we should use 'data'.
        // ThermoPro likely usually uses empty or specific ID.
        // If we have 'data' (payload) and it starts with 0xC2 (if ID was processed) OR...
        // Wait, if Theengs says index 0 is C2, and index 0 is start of ID+Payload..
        // Then ID starts with C2?
        // Manufacturer ID 0xC2xx?

        // Let's rely on NAME matching heavily as it is unique enough with "TP35x".
        // And try to finding the payload in rawData that matches structure.

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        // Find payload - either with C2 prefix or standard manufacturer data
        val payload = findManufacturerDataWithC2(rawData)
            ?: findManufacturerDataPayload(rawData)
            ?: (if (data.isNotEmpty()) data else null)

        if (payload == null || payload.size < 5) {
            return SensorData(beaconType = "ThermoPro (No Data)")
        }

        try {
            // Format from C++ TpParser (Reverse Engineered):
            // Byte 0: Header/Unknown (often 0x00 or 0xC2)
            // Byte 1-2: Temperature (LE, signed) / 10.0
            // Byte 3: Humidity (%)
            // Byte 4: Battery (%)

            // Check if first byte is header (0x00 or 0xC2) - skip it
            val offset = when (payload[0].toInt() and 0xFF) {
                0x00, 0xC2 -> 1
                else -> 0 // Assume direct data
            }

            if (payload.size < offset + 4) {
                return SensorData(beaconType = "ThermoPro (Short Data)")
            }

            // Temperature: Little Endian short, / 10.0
            val tLow = payload[offset].toInt() and 0xFF
            val tHigh = payload[offset + 1].toInt() and 0xFF
            val tRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tRaw.toDouble() / 10.0

            // Humidity: Byte after temp
            val hum = (payload[offset + 2].toInt() and 0xFF).toDouble()

            // Battery: Byte after humidity (if available)
            val battery = if (payload.size > offset + 3) {
                val bat = payload[offset + 3].toInt() and 0xFF
                if (bat in 0..100) bat else null
            } else {
                null
            }

            // Sanity checks
            if (hum > 100 || temp < -50 || temp > 100) {
                return SensorData(beaconType = "ThermoPro (Invalid Range)")
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = battery,
                beaconType = "ThermoPro TP35x",
                rawData = buildString {
                    append("TP: %.1f°C, %.0f%%".format(temp, hum))
                    battery?.let { append(", Bat: $it%") }
                }
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "ThermoPro (Parse Error)")
        }
    }

    /**
     * Find any Manufacturer Data payload (without requiring C2 header)
     */
    private fun findManufacturerDataPayload(rawData: ByteArray?): ByteArray? {
        if (rawData == null) return null
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0xFF && len >= 5) {
                // Manufacturer Data - skip 2-byte ID, return payload
                // Content at i+2 is ID (2 bytes), payload starts at i+4
                if (len > 4) {
                    return rawData.copyOfRange(i + 4, i + 1 + len)
                }
            }
            i += 1 + len
        }
        return null
    }

    private fun findManufacturerDataWithC2(rawData: ByteArray?): ByteArray? {
        if (rawData == null) return null
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0xFF) {
                // Manufacturer Data
                // Check if content starts with C2 (Header? or ID?)
                // Content starts at i + 2
                if (len > 0) {
                    val firstByte = rawData[i + 2].toInt() and 0xFF
                    // Check for C2
                    if (firstByte == 0xC2) {
                        return rawData.copyOfRange(i + 2, i + 1 + len)
                    }
                    // Or maybe ID is first 2 bytes, and Payload starts with C2?
                    // If ID is something else, we might miss it.
                    // But Theengs implies index 0 is C2.

                    // Also check if index 2 is C2 (if ID is 2 bytes and C2 is payload start)
                    if (len > 2) {
                        val thirdByte = rawData[i + 4].toInt() and 0xFF
                        if (thirdByte == 0xC2) {
                            // Return the payload part? Or full?
                            // Decoder expects index 0 is C2.
                            return rawData.copyOfRange(i + 4, i + 1 + len)
                        }
                    }
                }
            }
            i += 1 + len
        }
        return null
    }

    private fun extractLocalName(rawData: ByteArray): String? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                return String(rawData.copyOfRange(i + 2, i + 1 + len), Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }
}
