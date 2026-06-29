package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Radioland RDL52832 beacon decoder. Theengs: RDL52832_json.h */
@Singleton
class RadiolandDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (rawData == null) return false
        val name = extractLocalName(rawData)
        return name == "RDL52832" && data != null && data.size == 48
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Radioland (Error)")

        try {
            // Radioland puts iBeacon-like data in Manufacturer Data
            // hex 40 -> byte 20 -> data[18]
            val major = extractInt(data, 18, 2)
            // hex 44 -> byte 22 -> data[20]
            val minor = extractInt(data, 20, 2)

            // Sensor data in Service Data (usually)
            // Theengs says "servicedata" indices 0, 4, 12...
            // Let's look for any service data chunk.
            val sd = findAnyServiceData(rawData)
            var temp: Double? = null
            var hum: Double? = null

            if (sd != null && sd.size >= 4) {
                temp = extractSignedInt(sd, 0, 2).toDouble() / 256.0
                hum = extractUnsignedInt(sd, 2, 2).toDouble() / 256.0
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                sensorStatus = "Major: $major, Minor: $minor",
                beaconType = "Radioland RDL52832",
                rawData = "RDL: Major=$major, Minor=$minor",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Radioland (Parse Error)")
        }
    }

    private fun extractInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            if (start + i < data.size) {
                res = (res shl 8) or (data[start + i].toInt() and 0xFF)
            }
        }
        return res
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
        return res.toShort().toInt()
    }

    private fun extractUnsignedInt(
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

    private fun findAnyServiceData(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16 && i + 3 < rawData.size) {
                return rawData.copyOfRange(i + 4, i + 1 + len)
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
