package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xiaomi Formaldehyde Detector (JQJCY01YM) decoder. Theengs: JQJCY01YM_json.h Service data starts
 * with 20df02 at index 2
 */
@Singleton
class XiaomiFormaldehydeDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        val serviceData = findServiceDataFE95(rawData) ?: return false

        // Check for 20df02 at index 2 (bytes 1-3)
        if (serviceData.size < 4) return false
        return serviceData[1] == 0x20.toByte() &&
            serviceData[2] == 0xDF.toByte() &&
            serviceData[3] == 0x02.toByte()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Formaldehyde (Error)")

        val serviceData =
            findServiceDataFE95(rawData)
                ?: return SensorData(beaconType = "Formaldehyde (No Data)")

        try {
            if (serviceData.size < 16) return SensorData(beaconType = "Formaldehyde (Short)")

            val dataTypeNibble = serviceData[11].toInt() and 0x0F

            var formaldehyde: Double? = null
            var temp: Double? = null
            var humidity: Double? = null
            var battery: Int? = null

            when (dataTypeNibble) {
                0x00 -> { // Formaldehyde
                    if (serviceData.size >= 16) {
                        val fLow = serviceData[14].toInt() and 0xFF
                        val fHigh = serviceData[15].toInt() and 0xFF
                        val fRaw = ((fHigh shl 8) or fLow).toShort()
                        formaldehyde = fRaw / 100.0
                    }
                }
                0x04 -> { // Temperature
                    if (serviceData.size >= 16) {
                        val tLow = serviceData[14].toInt() and 0xFF
                        val tHigh = serviceData[15].toInt() and 0xFF
                        val tRaw = ((tHigh shl 8) or tLow).toShort()
                        temp = tRaw / 10.0
                    }
                }
                0x06 -> { // Humidity
                    if (serviceData.size >= 16) {
                        val hLow = serviceData[14].toInt() and 0xFF
                        val hHigh = serviceData[15].toInt() and 0xFF
                        val hRaw = ((hHigh shl 8) or hLow).toShort()
                        humidity = hRaw / 10.0
                    }
                }
                0x0A -> { // Battery
                    if (serviceData.size >= 15) {
                        battery = serviceData[14].toInt() and 0xFF
                    }
                }
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                formaldehydeMgm3 = formaldehyde,
                batteryLevel = battery,
                beaconType = "Xiaomi Formaldehyde Detector",
                rawData = "JQJCY01YM: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Formaldehyde (Parse Error)")
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
