package io.blueeye.core.decoders.misc.moko

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Mokosmart BeaconX Pro (MBXPRO) decoder. Theengs: MBXPRO_json.h Service UUID: FEAB */
@Singleton
class MokosmartDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FEAB
        return serviceUuids.any { it.lowercase().contains("feab") }
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Mokosmart (Error)")

        val serviceData =
            findServiceData(rawData, 0xFEAB)
                ?: return SensorData(beaconType = "Mokosmart (No Data)")

        if (serviceData.isEmpty()) return SensorData(beaconType = "Mokosmart (Short)")

        try {
            val type = serviceData[0].toInt() and 0xFF
            var temp: Double? = null
            var hum: Double? = null
            var volt: Double? = null
            var accX: Double? = null
            var accY: Double? = null
            var accZ: Double? = null

            when (type) {
                0x40 -> { // Voltage
                    if (serviceData.size >= 5) {
                        val vRaw =
                            (serviceData[3].toInt() and 0xFF shl 8) or
                                (serviceData[4].toInt() and 0xFF)
                        volt = vRaw / 1000.0
                    }
                }
                0x60 -> { // Accel + Voltage
                    if (serviceData.size >= 14) {
                        val xRaw =
                            (serviceData[6].toInt() and 0xFF shl 8) or
                                (serviceData[7].toInt() and 0xFF)
                        accX = (xRaw / 10000.0) * 9.80665
                        val yRaw =
                            (serviceData[8].toInt() and 0xFF shl 8) or
                                (serviceData[9].toInt() and 0xFF)
                        accY = (yRaw / 10000.0) * 9.80665
                        val zRaw =
                            (serviceData[10].toInt() and 0xFF shl 8) or
                                (serviceData[11].toInt() and 0xFF)
                        accZ = (zRaw / 10000.0) * 9.80665
                        val vRaw =
                            (serviceData[12].toInt() and 0xFF shl 8) or
                                (serviceData[13].toInt() and 0xFF)
                        volt = vRaw / 1000.0
                    }
                }
                0x70 -> { // Temp + Hum + Voltage
                    if (serviceData.size >= 9) {
                        val tRaw =
                            (serviceData[3].toInt() and 0xFF shl 8) or
                                (serviceData[4].toInt() and 0xFF)
                        temp = tRaw / 10.0
                        val hRaw =
                            (serviceData[5].toInt() and 0xFF shl 8) or
                                (serviceData[6].toInt() and 0xFF)
                        hum = hRaw / 10.0
                        val vRaw =
                            (serviceData[7].toInt() and 0xFF shl 8) or
                                (serviceData[8].toInt() and 0xFF)
                        volt = vRaw / 1000.0
                    }
                }
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                voltageV = volt,
                accelerationX = accX,
                accelerationY = accY,
                accelerationZ = accZ,
                beaconType = "Mokosmart BeaconX Pro",
                rawData = "Mokosmart: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Mokosmart (Parse Error)")
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
