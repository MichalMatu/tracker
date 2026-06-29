package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xiaomi MiJia Round (LYWSDCGQ) temperature/humidity sensor decoder. Theengs: LYWSDCGQ_json.h
 * Service data starts with 20aa01 at index 2
 */
@Singleton
class XiaomiMiJiaDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for FE95 service data
        val serviceData = findServiceDataFE95(rawData) ?: return false

        // Check for 20aa01 at index 2 (bytes 1-2)
        if (serviceData.size < 3) return false
        return serviceData[1] == 0x20.toByte() &&
            serviceData[2] == 0xAA.toByte() &&
            (serviceData.size > 3 && serviceData[3] == 0x01.toByte())
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "MiJia (Error)")

        val serviceData =
            findServiceDataFE95(rawData) ?: return SensorData(beaconType = "MiJia (No Data)")

        try {
            // Parse based on data type nibble at hex index 23 (byte 11, low nibble)
            if (serviceData.size < 15) return SensorData(beaconType = "MiJia (Short)")

            val dataTypeNibble = serviceData[11].toInt() and 0x0F

            var temp: Double? = null
            var humidity: Double? = null
            var battery: Int? = null

            when (dataTypeNibble) {
                0x0A -> { // Battery
                    if (serviceData.size >= 15) {
                        battery = serviceData[14].toInt() and 0xFF
                    }
                }
                0x0D -> { // Temperature + Humidity
                    if (serviceData.size >= 17) {
                        val tLow = serviceData[14].toInt() and 0xFF
                        val tHigh = serviceData[15].toInt() and 0xFF
                        val tempRaw = ((tHigh shl 8) or tLow).toShort()
                        temp = tempRaw / 10.0

                        val hLow = serviceData[16].toInt() and 0xFF
                        val hHigh =
                            if (serviceData.size > 17) serviceData[17].toInt() and 0xFF else 0
                        val humRaw = ((hHigh shl 8) or hLow).toShort()
                        humidity = humRaw / 10.0
                    }
                }
                0x04 -> { // Temperature only
                    if (serviceData.size >= 16) {
                        val tLow = serviceData[14].toInt() and 0xFF
                        val tHigh = serviceData[15].toInt() and 0xFF
                        val tempRaw = ((tHigh shl 8) or tLow).toShort()
                        temp = tempRaw / 10.0
                    }
                }
                0x06 -> { // Humidity only
                    if (serviceData.size >= 16) {
                        val hLow = serviceData[14].toInt() and 0xFF
                        val hHigh = serviceData[15].toInt() and 0xFF
                        val humRaw = ((hHigh shl 8) or hLow).toShort()
                        humidity = humRaw / 10.0
                    }
                }
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                batteryLevel = battery,
                beaconType = "Xiaomi MiJia Round",
                rawData = "MiJia: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "MiJia (Parse Error)")
        }
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
