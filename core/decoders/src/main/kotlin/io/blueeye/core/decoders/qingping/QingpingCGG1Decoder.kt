package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Qingping Temp & RH Monitor (CGG1) decoder.
 * Supports Stock, ATC1441, and PVVX firmware formats.
 * Theengs: CGG1_json.h
 */
@Singleton
class QingpingCGG1Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Stock firmware uses FDCD
        val isQingpingUuid = serviceUuids.any { it.lowercase().contains("fdcd") }
        // Custom firmwares use 181A
        val isEnvSensingUuid = serviceUuids.any { it.lowercase().contains("181a") }

        if (!isQingpingUuid && !isEnvSensingUuid) return false

        val serviceDataFDCD = findServiceData(rawData, 0xFDCD)
        val serviceData181A = findServiceData(rawData, 0x181A)

        // STOCK: len 34 (hex) = 17 bytes, byte 2 is 0x07 or 0x16
        if (serviceDataFDCD != null && serviceDataFDCD.size == 17) {
            val type = serviceDataFDCD[2].toInt() and 0xFF
            if (type == 0x07 || type == 0x16) return true
        }

        // ATC1441: len 26 (hex) = 13 bytes, name starts with CGG
        if (serviceData181A != null && serviceData181A.size == 13) {
            return true // Typically combined with name check in higher level, but here we check size
        }

        // PVVX: len 30 (hex) = 15 bytes
        if (serviceData181A != null && serviceData181A.size == 15) {
            return true
        }

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Qingping CGG1 (Error)")

        val fdcdData = findServiceData(rawData, 0xFDCD)
        if (fdcdData != null && fdcdData.size == 17) {
            return decodeStock(fdcdData)
        }

        val envData = findServiceData(rawData, 0x181A)
        if (envData != null) {
            return when (envData.size) {
                13 -> decodeATC1441(envData)
                15 -> decodePVVX(envData)
                else -> SensorData(beaconType = "Qingping CGG1 (Unknown EnvData)")
            }
        }

        return SensorData(beaconType = "Qingping CGG1", rawData = "Unknown format")
    }

    private fun decodeStock(data: ByteArray): SensorData {
        // STOCK:
        // tempc: offset 10 (hex 20), len 2 bytes, unsigned, /10
        // hum: offset 12 (hex 24), len 2 bytes, unsigned, /10
        // batt: offset 16 (hex 32), len 1 byte, unsigned

        val tempRaw = ((data[11].toInt() and 0xFF) shl 8) or (data[10].toInt() and 0xFF)
        val temp = tempRaw / 10.0

        val humRaw = ((data[13].toInt() and 0xFF) shl 8) or (data[12].toInt() and 0xFF)
        val hum = humRaw / 10.0

        val batt = data[16].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = hum,
            batteryLevel = batt,
            beaconType = "Qingping CGG1 (Stock)",
            rawData = "FDCD: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodeATC1441(data: ByteArray): SensorData {
        // ATC1441:
        // tempc: offset 6 (hex 12), len 2 bytes, /10
        // hum: offset 8 (hex 16), len 1 byte
        // batt: offset 9 (hex 18), len 1 byte
        // volt: offset 10 (hex 20), len 2 bytes, /1000

        val tempRaw = ((data[7].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
        val temp = tempRaw / 10.0

        val hum = (data[8].toInt() and 0xFF).toDouble()
        val batt = data[9].toInt() and 0xFF

        val voltRaw = ((data[11].toInt() and 0xFF) shl 8) or (data[10].toInt() and 0xFF)
        val volt = voltRaw / 1000.0

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = hum,
            batteryLevel = batt,
            voltageV = volt,
            beaconType = "Qingping CGG1 (ATC1441)",
            rawData = "ATC: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodePVVX(data: ByteArray): SensorData {
        // PVVX:
        // tempc: offset 6 (hex 12), len 2 bytes, signed, /100
        // hum: offset 8 (hex 16), len 2 bytes, signed, /100
        // batt: offset 12 (hex 24), len 1 byte
        // volt: offset 10 (hex 20), len 2 bytes, signed, /1000

        val tempRaw = (((data[7].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)).toShort()
        val temp = tempRaw / 100.0

        val humRaw = (((data[9].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)).toShort()
        val hum = humRaw / 100.0

        val voltRaw = (((data[11].toInt() and 0xFF) shl 8) or (data[10].toInt() and 0xFF)).toShort()
        val volt = voltRaw / 1000.0

        val batt = data[12].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = hum,
            batteryLevel = batt,
            voltageV = volt,
            beaconType = "Qingping CGG1 (PVVX)",
            rawData = "PVVX: ${data.joinToString("") { "%02X".format(it) }}",
        )
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
