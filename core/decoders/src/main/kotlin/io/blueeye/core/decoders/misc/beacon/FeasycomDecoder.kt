package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Feasycom Beacon decoder. Theengs: FEASY_json.h Service UUID: FFF0 */
@Singleton
class FeasycomDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FFF0
        if (!serviceUuids.any { it.lowercase().contains("fff0") }) return false

        val serviceData = findServiceDataFFF0(rawData) ?: return false

        // Service data = 22 hex chars = 11 bytes
        return serviceData.size == 11
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Feasycom (Error)")

        val serviceData =
            findServiceDataFFF0(rawData) ?: return SensorData(beaconType = "Feasycom (No Data)")

        if (serviceData.size < 11) return SensorData(beaconType = "Feasycom (Short)")

        try {
            // beaconmodel: byte 0
            val modelCode = "%02x".format(serviceData[0])
            val modelName =
                when (modelCode) {
                    "15" -> "BP102"
                    "19" -> "BP109"
                    "1a" -> "BP103"
                    "1b" -> "BP104"
                    "1c" -> "BP201"
                    "1d" -> "BP106"
                    "1e" -> "BP101"
                    "24" -> "BP120"
                    "27" -> "BP108"
                    "28" -> "BP108N"
                    "29" -> "BP103B"
                    "46" -> "BP104D"
                    else -> modelCode.uppercase()
                }

            // batt: byte 10
            val battByte = serviceData[10].toInt() and 0xFF
            val isPluggedIn = battByte == 0x65
            val battery = if (isPluggedIn) null else (battByte and 0x7F)

            return SensorData(
                batteryLevel = battery,
                sensorStatus = if (isPluggedIn) "Plugged in" else null,
                beaconType = "Feasycom $modelName",
                rawData = "Feasycom: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Feasycom (Parse Error)")
        }
    }

    private fun findServiceDataFFF0(rawData: ByteArray): ByteArray? {
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
                    // FFF0 in LE: F0 FF
                    if (uLow == 0xF0 && uHigh == 0xFF) {
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
