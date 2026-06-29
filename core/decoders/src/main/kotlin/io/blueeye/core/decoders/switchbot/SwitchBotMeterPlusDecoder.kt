package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SwitchBot Meter (Plus) (THX1/W230150X) decoder. Theengs: SBMT_json.h */
@Singleton
class SwitchBotMeterPlusDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (rawData == null) return false

        // Option 1: Service Data 0x54 or 0x69
        val serviceData = findServiceData(rawData, 0xFD3D) ?: findServiceData(rawData, 0x0D00)
        if (serviceData != null && serviceData.size >= 12) {
            val firstByte = serviceData[0].toInt() and 0xFF
            if (firstByte == 0x54 || firstByte == 0x69) return true
        }

        // Option 2: Name WoSensorTH and manufacturerdata 26 bytes
        val name = extractLocalName(rawData)
        if (name == "WoSensorTH" && data != null && data.size == 24) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "SwitchBot Meter Plus (Error)")

        val serviceData = findServiceData(rawData, 0xFD3D) ?: findServiceData(rawData, 0x0D00)

        try {
            if (serviceData != null && serviceData.size >= 10) {
                // Decode from service data (offsets hex: batt 8, cal 14, temp 16, hum 22)
                // Assuming serviceData excludes 2-byte UUID.
                // start of SD payload is hex 8 (byte 4 of record).
                // Hex H -> serviceData[(H - 8) / 2]
                // hex 8 -> data[0]. hex 14 -> data[3]. hex 16 -> data[4]. hex 22 -> data[7].
                val battery = serviceData[0].toInt() and 0x7F
                val cal = (serviceData[3].toInt() and 0xFF) / 10.0
                val b4 = serviceData[4].toInt() and 0xFF
                val b5 = serviceData[5].toInt() and 0xFF
                val tRaw = ((b4 and 0x7F) shl 8) or b5
                val bit3 = (b4 and 0x08) != 0
                val temp =
                    if (!bit3) (tRaw.toDouble() + cal) * -1.0 else tRaw.toDouble() + cal - 128.0
                val hum = serviceData[7].toInt() and 0x7F

                return SensorData(
                    temperatureCelcius = temp,
                    humidityPercent = hum.toDouble(),
                    batteryLevel = battery,
                    beaconType = "SwitchBot Meter Plus",
                    rawData = "SBMT(S): %.1f C, %.0f%%".format(temp, hum.toDouble()),
                )
            } else if (data.size >= 24) {
                // Decode from manufacturer data (offsets hex: cal 40, temp 44, hum 46)
                // data[0] is byte 2 (hex 4).
                // hex 40 -> data[18]. hex 44 -> data[20]. hex 46 -> data[21].
                val cal = (data[18].toInt() and 0xFF) / 10.0
                val b20 = data[20].toInt() and 0xFF
                val b21 = data[21].toInt() and 0xFF
                val tRaw = ((b20 and 0x7F) shl 8) or b21
                val bit3 = (b20 and 0x08) != 0
                val temp =
                    if (!bit3) (tRaw.toDouble() + cal) * -1.0 else tRaw.toDouble() + cal - 128.0
                val hum = data[23].toInt() and 0x7F

                return SensorData(
                    temperatureCelcius = temp,
                    humidityPercent = hum.toDouble(),
                    beaconType = "SwitchBot Meter Plus",
                    rawData = "SBMT(M): %.1f C, %.0f%%".format(temp, hum.toDouble()),
                )
            }
            return SensorData(beaconType = "SwitchBot Meter Plus (Partial Data)")
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Meter Plus (Parse Error)")
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
