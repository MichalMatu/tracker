package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SwitchBot Outdoor Meter (W340001X) decoder. Theengs: SBOT_json.h */
@Singleton
class SwitchBotOutdoorMeterDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (rawData == null) return false

        // Option 1: Service Data 0x77 and manufacturer data 28
        val serviceData = findServiceData(rawData, 0xFD3D)
        if (serviceData != null &&
            serviceData.isNotEmpty() &&
            (serviceData[0].toInt() and 0xFF) == 0x77
        ) {
            if (data != null && data.size == 26) return true
        }

        // Option 2: Name WoIOSensorTH and manufacturerdata 28
        val name = extractLocalName(rawData)
        if (name == "WoIOSensorTH" && data != null && data.size == 26) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        if (data.size < 26) return SensorData(beaconType = "SwitchBot Outdoor Meter (Short)")

        val serviceData = findServiceData(rawData!!, 0xFD3D)

        try {
            // temp: byte 22-23 (index 22,2), calibration byte 21
            val cal = (data[19].toInt() and 0xFF) / 10.0
            val b22 = data[20].toInt() and 0xFF
            val b23 = data[21].toInt() and 0xFF
            val tRaw = ((b22 and 0x7F) shl 8) or b23
            val bit3 = (b22 and 0x08) != 0
            val temp = if (!bit3) (tRaw.toDouble() + cal) * -1.0 else tRaw.toDouble() + cal - 128.0

            // hum: byte 24-25 & 127
            val hum = data[23].toInt() and 0x7F

            // batt: servicedata byte 4 & 127
            val battery =
                if (serviceData != null && serviceData.size > 4) {
                    serviceData[4].toInt() and 0x7F
                } else {
                    null
                }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum.toDouble(),
                batteryLevel = battery,
                beaconType = "SwitchBot Outdoor Meter",
                rawData = "SBOT: %.1f C, %.0f%%".format(temp, hum.toDouble()),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Outdoor Meter (Parse Error)")
        }
    }

    private fun findServiceData(
        rawData: ByteArray,
        uuid16: Int,
    ): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16 && i + 3 < rawData.size) {
                val uLow = rawData[i + 2].toInt() and 0xFF
                val uHigh = rawData[i + 3].toInt() and 0xFF
                if (((uHigh shl 8) or uLow) == uuid16) {
                    val payloadLen = len - 3
                    if (payloadLen > 0) return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
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
