package io.blueeye.core.decoders.misc.tuya

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Tuya THB1 / BTH01 / TH05F decoder. Theengs: THB1_json.h */
@Singleton
class TuyaTHBDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false
        val name = extractLocalName(rawData) ?: return false
        if (!listOf("THB1", "BTH01", "TH05F").contains(name)) return false

        val serviceData = findServiceData(rawData, 0xFCD2)
        return serviceData != null &&
            serviceData.size >= 28 &&
            (serviceData[0].toInt() and 0xFF) == 0x40
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Tuya THB (Error)")
        val serviceData =
            findServiceData(rawData, 0xFCD2)
                ?: return SensorData(beaconType = "Tuya THB (No Data)")

        try {
            var temp: Double? = null
            var hum: Double? = null
            var battery: Int? = null
            var voltage: Double? = null

            // BTHome-like TLV format in Service Data
            // Theengs use conditions: servicedata 10 == 02 for temp, 16 == 03 for hum

            // temp: byte 12-15 / 100.0 (if byte 10 is 02)
            if (serviceData.size >= 16 && (serviceData[10].toInt() and 0xFF) == 0x02) {
                temp = extractSignedInt(serviceData, 12, 4) / 100.0
            }

            // hum: byte 18-21 / 100.0 (if byte 16 is 03)
            if (serviceData.size >= 22 && (serviceData[16].toInt() and 0xFF) == 0x03) {
                hum = extractUnsignedInt(serviceData, 18, 4) / 100.0
            }

            // batt: byte 8-9 (if byte 6 is 01)
            if (serviceData.size >= 10 && (serviceData[6].toInt() and 0xFF) == 0x01) {
                battery = extractUnsignedInt(serviceData, 8, 2).toInt()
            }

            // volt: byte 24-27 (if byte 22 is 0c)
            if (serviceData.size >= 28 && (serviceData[22].toInt() and 0xFF) == 0x0C) {
                voltage = extractSignedInt(serviceData, 24, 4) / 1000.0
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = battery,
                voltageV = voltage,
                beaconType = "Tuya Thermo-Hygrometer",
                rawData = "Tuya: %.1f C, %.1f%%".format(temp ?: 0.0, hum ?: 0.0),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Tuya THB (Parse Error)")
        }
    }

    private fun extractSignedInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            res = (res shl 8) or (data[start + i].toInt() and 0xFF)
        }
        return res
    }

    private fun extractUnsignedInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Long {
        var res = 0L
        for (i in 0 until len) {
            res = (res shl 8) or (data[start + i].toLong() and 0xFF)
        }
        return res
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
