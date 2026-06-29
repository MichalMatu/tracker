package io.blueeye.core.decoders.misc.sensor

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sensor Easy decoders (MAG, RHT, TEMP, TPROBE). Theengs: SE_MAG_json.h, SE_RHT_json.h,
 * SE_TEMP_json.h, SE_TPROBE_json.h Condition: Name matches and UUID 0x2A6E, 0x2A6F, or 0x2A06 in
 * Service Data.
 */
@Singleton
class SensorEasyDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false
        val name = extractLocalName(rawData) ?: return false

        // Conditions:
        // MAG: Name contains " MAG", UUID 2A06, SD size 4, byte 0 is SD type? No, SD size 4.
        // RHT: Name contains " RHT ", UUID 2A6E or 2A6F.
        // TEMP: Name contains " T ", UUID 2A6E, SD size 4.
        // TPROBE: Name contains " TPROBE", UUID 2A6E, SD size 4.

        return (name.contains(" MAG") && serviceUuids.any { it.contains("2a06") }) ||
            (
                name.contains(" RHT ") &&
                    (
                        serviceUuids.any { it.contains("2a6e") } ||
                            serviceUuids.any { it.contains("2a6f") }
                        )
                ) ||
            (name.contains(" T ") && serviceUuids.any { it.contains("2a6e") }) ||
            (name.contains(" TPROBE") && serviceUuids.any { it.contains("2a6e") })
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Sensor Easy (Error)")
        val name = extractLocalName(rawData) ?: "Sensor Easy"

        try {
            var temp: Double? = null
            var hum: Double? = null
            var voltage: Double? = null
            var doorOpen: Boolean? = null
            var type = "Sensor Easy Generic"

            // Common voltage in Manufacturer Data: hex 12,4 (byte 6-9 in MD), condition on byte 4
            // being F2
            // hex 8 -> byte 4 -> data[2]
            if (data.size >= 8 && (data[2].toInt() and 0xFF) == 0xF2) {
                // hex 12 -> byte 6 -> data[4]
                val vHigh = data[4].toInt() and 0xFF
                val vLow = data[5].toInt() and 0xFF
                val vRaw = (vHigh shl 8) or vLow
                voltage = vRaw / 1000.0
            }

            // Temperature/Humidity in Service Data
            val sd2A6E = findServiceData(rawData, 0x2A6E)
            val sd2A6F = findServiceData(rawData, 0x2A6F)
            val sd2A06 = findServiceData(rawData, 0x2A06)

            if (name.contains(" RHT ")) {
                type = "Sensor Easy RHT"
                if (sd2A6E != null && sd2A6E.size >= 4) {
                    val tHigh = sd2A6E[0].toInt() and 0xFF
                    val tLow = sd2A6E[1].toInt() and 0xFF
                    temp = ((tHigh shl 8) or tLow).toShort() / 100.0
                }
                if (sd2A6F != null && sd2A6F.size >= 1) {
                    // RHT hum: hex 0,2 -> 1 byte
                    hum = sd2A6F[0].toDouble()
                }
            } else if (name.contains(" MAG")) {
                type = "Sensor Easy MAG"
                if (sd2A06 != null && sd2A06.size >= 2) {
                    // byte 1 bit 0
                    doorOpen =
                        (sd2A06[1].toInt() and 0x01) ==
                        0 // bit_static_value 1,0,true,false -> 0 is true
                }
            } else if (name.contains(" TPROBE") || name.contains(" T ")) {
                type =
                    if (name.contains(" TPROBE")) {
                        "Sensor Easy Temp Probe"
                    } else {
                        "Sensor Easy Temp"
                    }
                if (sd2A6E != null && sd2A6E.size >= 4) {
                    val tHigh = sd2A6E[0].toInt() and 0xFF
                    val tLow = sd2A6E[1].toInt() and 0xFF
                    temp = ((tHigh shl 8) or tLow).toShort() / 100.0
                }
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                voltageV = voltage,
                doorOpen = doorOpen,
                beaconType = type,
                rawData =
                "SE: T=%.1f C, H=%.1f, V=%.2f V".format(
                    temp ?: 0.0,
                    hum ?: 0.0,
                    voltage ?: 0.0,
                ),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Sensor Easy (Parse Error)")
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
