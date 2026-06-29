package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xiaomi MiFlora Plant Sensor (HHCCJCY01HHCC / HHCCJCY10) decoder. Theengs: HHCCJCY01HHCC_json.h,
 * HHCCJCY10_json.h Service UUID: FE95 or FD50
 */
@Singleton
class MiFloraDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for FE95 (old format) or FD50 (new format)
        val hasFe95 = serviceUuids.any { it.lowercase().contains("fe95") }
        val hasFd50 = serviceUuids.any { it.lowercase().contains("fd50") }

        if (!hasFe95 && !hasFd50) return false

        if (hasFd50) {
            val serviceData = findServiceDataFD50(rawData) ?: return false
            return serviceData.size >= 9
        }

        if (hasFe95) {
            val serviceData = findServiceDataFE95(rawData) ?: return false
            if (serviceData.size < 16) return false
            // Check for MiFlora device types: 9800 or bc03
            val typeByte0 = serviceData[2].toInt() and 0xFF
            val typeByte1 = serviceData[3].toInt() and 0xFF
            return (typeByte0 == 0x98 && typeByte1 == 0x00) ||
                (typeByte0 == 0xBC && typeByte1 == 0x03)
        }

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "MiFlora (Error)")

        // Try FD50 format first (newer)
        val fd50Data = findServiceDataFD50(rawData)
        if (fd50Data != null && fd50Data.size >= 9) {
            return decodeFD50(fd50Data)
        }

        // Try FE95 format (older)
        val fe95Data = findServiceDataFE95(rawData)
        if (fe95Data != null && fe95Data.size >= 16) {
            return decodeFE95(fe95Data)
        }

        return SensorData(beaconType = "MiFlora (No Data)")
    }

    private fun decodeFD50(data: ByteArray): SensorData {
        // FD50 format: compact data
        // Byte 0: Moisture
        // Bytes 1-2: Temperature (BE, /10)
        // Bytes 3-5: Illuminance (LE)
        // Byte 6: Battery
        // Bytes 7-8: Fertility

        val moisture = (data[0].toInt() and 0xFF).toDouble()

        val tHigh = data[1].toInt() and 0xFF
        val tLow = data[2].toInt() and 0xFF
        val tempRaw = (tHigh shl 8) or tLow
        val temp = tempRaw / 10.0

        val l1 = data[3].toLong() and 0xFF
        val l2 = data[4].toLong() and 0xFF
        val l3 = data[5].toLong() and 0xFF
        val lux = (l3 shl 16) or (l2 shl 8) or l1

        val battery = data[6].toInt() and 0xFF

        val fLow = data[7].toInt() and 0xFF
        val fHigh = data[8].toInt() and 0xFF
        val fertility = (fHigh shl 8) or fLow

        return SensorData(
            temperatureCelcius = temp,
            soilMoisturePercent = moisture,
            illuminanceLux = lux.toDouble(),
            batteryLevel = battery,
            fertilityUsCm = fertility,
            beaconType = "MiFlora (HHCCJCY10)",
            rawData = "MiFlora: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodeFE95(data: ByteArray): SensorData {
        // FE95 format: Xiaomi MiBeacon format with TLV-style properties
        // Parse the data type at byte 12 (hex 24-27)

        var temp: Double? = null
        var moisture: Double? = null
        var lux: Double? = null
        var fertility: Int? = null

        if (data.size >= 17) {
            val dataType0 = data[12].toInt() and 0xFF
            val dataType1 = data[13].toInt() and 0xFF

            // Each message contains one sensor value
            when {
                dataType0 == 0x04 && dataType1 == 0x10 -> {
                    // Temperature
                    val tLow = data[15].toInt() and 0xFF
                    val tHigh = data[16].toInt() and 0xFF
                    val tempRaw = ((tHigh shl 8) or tLow).toShort()
                    temp = tempRaw / 10.0
                }
                dataType0 == 0x08 && dataType1 == 0x10 -> {
                    // Moisture
                    moisture = (data[15].toInt() and 0xFF).toDouble()
                }
                dataType0 == 0x07 && dataType1 == 0x10 -> {
                    // Illuminance
                    if (data.size >= 18) {
                        val l1 = data[15].toLong() and 0xFF
                        val l2 = data[16].toLong() and 0xFF
                        val l3 = data[17].toLong() and 0xFF
                        lux = ((l3 shl 16) or (l2 shl 8) or l1).toDouble()
                    }
                }
                dataType0 == 0x09 && dataType1 == 0x10 -> {
                    // Fertility
                    val fLow = data[15].toInt() and 0xFF
                    val fHigh = data[16].toInt() and 0xFF
                    fertility = (fHigh shl 8) or fLow
                }
            }
        }

        return SensorData(
            temperatureCelcius = temp,
            soilMoisturePercent = moisture,
            illuminanceLux = lux,
            fertilityUsCm = fertility,
            beaconType = "MiFlora (HHCCJCY01)",
            rawData = "MiFlora: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun findServiceDataFD50(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16 && i + 3 < rawData.size) {
                val uLow = rawData[i + 2].toInt() and 0xFF
                val uHigh = rawData[i + 3].toInt() and 0xFF
                // FD50 in LE: 50 FD
                if (uLow == 0x50 && uHigh == 0xFD) {
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

    private fun findServiceDataFE95(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16 && i + 3 < rawData.size) {
                val uLow = rawData[i + 2].toInt() and 0xFF
                val uHigh = rawData[i + 3].toInt() and 0xFF
                // FE95 in LE: 95 FE
                if (uLow == 0x95 && uHigh == 0xFE) {
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
