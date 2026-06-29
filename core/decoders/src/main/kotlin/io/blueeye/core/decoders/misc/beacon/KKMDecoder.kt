package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** KKM Beacon decoder (K6P, K9). Theengs: KKM_K6P_json.h, KKM_K9_json.h Service UUID: FEAA */
@Singleton
class KKMDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FEAA
        if (!serviceUuids.any { it.lowercase().contains("feaa") }) return false

        val serviceData = findServiceData(rawData, 0xFEAA) ?: return false

        // K6P: hex 18 (9 bytes), starts with 210107
        // K9: hex 30 (15 bytes), starts with 21010f
        if (serviceData.size == 9 && serviceData.startsWith("210107")) return true
        if (serviceData.size == 15 && serviceData.startsWith("21010f")) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "KKM (Error)")

        val serviceData =
            findServiceData(rawData, 0xFEAA) ?: return SensorData(beaconType = "KKM (No Data)")

        try {
            val isK9 = serviceData.size == 15
            val modelName = if (isK9) "KKM K9" else "KKM K6P"

            // volt: hex 6 (byte 3), 2 bytes
            val vHigh = serviceData[3].toInt() and 0xFF
            val vLow = serviceData[4].toInt() and 0xFF
            val volt = ((vHigh shl 8) or vLow) / 1000.0

            // temp: byte 5 signed + (byte 6 / 256.0)
            val tBase = serviceData[5].toInt().toByte().toDouble()
            val tCal = (serviceData[6].toInt() and 0xFF) / 256.0
            val temp = tBase + tCal

            // hum: byte 7 + (byte 8 / 256.0)
            val hBase = (serviceData[7].toInt() and 0xFF).toDouble()
            val hCal = (serviceData[8].toInt() and 0xFF) / 256.0
            val hum = hBase + hCal

            var accX: Double? = null
            var accY: Double? = null
            var accZ: Double? = null

            if (isK9 && serviceData.size >= 15) {
                // accx: byte 9, 2 bytes signed
                val axHigh = serviceData[9].toInt() and 0xFF
                val axLow = serviceData[10].toInt() and 0xFF
                accX = ((axHigh shl 8) or axLow).toShort().toDouble()

                // accy: byte 11, 2 bytes signed
                val ayHigh = serviceData[11].toInt() and 0xFF
                val ayLow = serviceData[12].toInt() and 0xFF
                accY = ((ayHigh shl 8) or ayLow).toShort().toDouble()

                // accz: byte 13, 2 bytes signed
                val azHigh = serviceData[13].toInt() and 0xFF
                val azLow = serviceData[14].toInt() and 0xFF
                accZ = ((azHigh shl 8) or azLow).toShort().toDouble()
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                voltageV = volt,
                accelerationX = accX,
                accelerationY = accY,
                accelerationZ = accZ,
                beaconType = modelName,
                rawData = "KKM: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "KKM (Parse Error)")
        }
    }

    private fun ByteArray.startsWith(hex: String): Boolean {
        val hexBytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (this.size < hexBytes.size) return false
        for (i in hexBytes.indices) {
            if (this[i] != hexBytes[i]) return false
        }
        return true
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
            if (type == 0x16) {
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    val foundUuid = (uHigh shl 8) or uLow
                    if (foundUuid == uuid16) {
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
